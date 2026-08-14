# GitHub Copilot Instructions — SignPDF Android App

> This file used to be a from-scratch build plan (Sprints 1-5). All of that is done; this is
> now a **current-state reference** so Copilot suggestions match the actual codebase instead of
> the original plan. Keep this in sync with `CLAUDE.md` (same facts, different audience/format)
> whenever architecture changes.

## Agent Behavior Rules

- Match existing patterns in this file over generic Android best practices when they conflict.
- Write complete, compilable Kotlin — no placeholders, no `TODO` stubs unless explicitly asked.
- No local Android SDK is assumed to be available in this environment — verify with
  `./gradlew tasks` (config-only check); real compilation is validated by CI on push.

---

## Project Identity

| Key | Value |
|---|---|
| App Name | SignPDF |
| Package Name | `com.wantox86.signpdf` |
| Language | Kotlin |
| Build System | Gradle Kotlin DSL (`build.gradle.kts`) |
| Architecture | MVVM + UseCase layer, **no DI framework** |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 34 |
| UI Binding | ViewBinding (NOT DataBinding, NOT Jetpack Compose) |
| Navigation | Navigation Component (single-activity) |
| Async | Kotlin Coroutines + Flow |

## Tech Stack — Actual Versions in Use

```kotlin
// gradle/libs.versions.toml
pdfboxAndroid = "2.0.27.0"      // com.tom-roush:pdfbox-android
signaturePad = "1.3.1"          // com.github.gcacace:signature-pad
coil = "2.6.0"
lifecycle = "2.8.0"
coroutines = "1.8.0"
navigation = "2.7.7"
recyclerview = "1.3.2"
viewpager2 = "1.1.0"
material = "1.12.0"
constraintlayout = "2.1.4"
agp = "8.3.2"
kotlin = "1.9.23"

// Cloud Signature Sync (feature/cloud-signature-sync branch, not yet merged to main)
retrofit = "2.11.0"             // + converter-kotlinx-serialization
okhttp = "4.12.0"
kotlinxSerialization = "1.6.3"
securityCrypto = "1.1.0-alpha06" // androidx.security:security-crypto
mockk = "1.13.11"                // test-only
robolectric = "4.13"             // test-only
```

**No Hilt, no DI framework.** It was in the original plan (`@HiltAndroidApp`, `AppModule`,
`kapt`) but was removed — no ViewModel ever actually used `@Inject`, every dependency is
constructed manually in the ViewModel constructor (`private val pdfRepository =
PdfRepository(app)`). Follow that pattern for new dependencies; don't reintroduce Hilt unless
the dependency graph genuinely outgrows manual wiring.

---

## Directory Structure (actual)

```
app/src/main/java/com/wantox86/signpdf/
├── SignPdfApplication.kt        # PDFBoxResourceLoader.init() + CrashHandler.install(); also hosts
│                                 # app-wide singletons for cloud sync (tokenStore, signatureRepository,
│                                 # authRepository, syncRepository) -- see "Cloud Signature Sync" below
├── CrashHandler.kt              # global crash handler, writes stack trace, shown as a
│                                 # selectable dialog on next launch (see MainActivity)
├── MainActivity.kt              # single activity, hosts NavController, handles ACTION_VIEW
├── data/
│   ├── PdfRepository.kt
│   ├── SignatureRepository.kt   # persists saved TTD/PARAF bitmaps to filesDir/signatures/*.png;
│   │                             # + saveBitmapFromSync/bitmapBytesFor/lastModifiedAt (cloud sync only)
│   ├── AuthRepository.kt        # login/logout/session-expiry; exposes StateFlow<AuthState>
│   ├── SyncRepository.kt        # client-side sync algorithm (see "Cloud Signature Sync" below)
│   ├── local/                   # TokenStorage (interface), TokenStore (impl), SignatureMetadataStore
│   └── remote/                  # ApiClient, AuthInterceptor, SignPdfApiService, dto/
├── domain/
│   ├── model/
│   │   ├── PdfDocument.kt
│   │   ├── SignatureOverlay.kt  # id, type, bitmap, pageIndex, x/y/width/height, createdAt
│   │   ├── AuthState.kt         # sealed: Guest / Authenticated(username)
│   │   ├── SyncState.kt         # sealed: Idle / Syncing / Synced(at) / Failed(message)
│   │   └── SignatureSlotMeta.kt # per-slot cloud sync bookkeeping
│   ├── usecase/
│   │   ├── RenderPdfPageUseCase.kt
│   │   ├── EmbedSignatureToPdfUseCase.kt
│   │   └── ExportPdfUseCase.kt
│   └── util/
│       └── PdfCoordinateConverter.kt   # pure math, has a unit test
└── ui/
    ├── home/            # HomeFragment/HomeViewModel -- includes the cloud-sync status bar
    ├── auth/             # LoginFragment, LoginViewModel
    ├── editor/         # PdfEditorFragment, PdfEditorViewModel, PdfPageAdapter, SignatureOverlayView, ZoomableContainer
    ├── signature/       # SignatureCanvasFragment, SignaturePickerBottomSheet, SignatureViewModel
    ├── preview/         # PdfPreviewFragment, PdfPreviewViewModel
    └── share/           # ShareHelper

app/src/test/java/.../domain/util/PdfCoordinateConverterTest.kt   # PDF coordinate math
app/src/test/java/.../ (feature/cloud-signature-sync branch)      # SyncRepositoryTest, SignatureMetadataStoreTest,
                                                                    # LoginViewModelTest, HomeViewModelTest,
                                                                    # MainDispatcherRule, FakeTokenStorage, FakeSignPdfApiService
```

There is no `SignatureSource.kt` (planned early, never used, deleted) and no `di/AppModule.kt`
(Hilt module, deleted with Hilt).

---

## Domain Models — Current Shape

```kotlin
// domain/model/SignatureOverlay.kt
data class SignatureOverlay(
    val id: String = UUID.randomUUID().toString(),
    val type: OverlayType,
    val bitmap: Bitmap,
    val pageIndex: Int,
    val x: Float,        // page-local pixel space (bitmap width, NOT screen pixels) — see below
    val y: Float,
    val width: Float,
    val height: Float,
    val createdAt: Long = System.currentTimeMillis()
)

enum class OverlayType { TTD, PARAF }
// UI-facing labels are English ("Sign" / "Initial") via R.string.overlay_type_ttd /
// overlay_type_paraf — the enum names themselves were kept as-is, only display strings changed.
```

```kotlin
// domain/model/PdfDocument.kt
data class PdfDocument(
    val uri: Uri,
    val fileName: String,   // real display name (with extension) resolved via ContentResolver,
                             // NOT uri.lastPathSegment (that's the SAF provider's document ID)
    val pageCount: Int,
    val outputPath: String? = null
)
```

---

## Critical Architecture Rules — Read Before Touching Overlay/Rendering Code

### 1. Overlays live inside each RecyclerView page item, not in one global view

`SignatureOverlayView` is a child of `item_pdf_page.xml` (one instance per page item), not a
single fullscreen view stacked over the whole RecyclerView. This was a deliberate fix for a bug
where "current page" was resolved via `findFirstVisibleItemPosition()`, so an overlay visually
dropped on the lower of two partially-visible pages got stored against the wrong page with
coordinates past its bottom edge — invisible once embedded. Full root-cause writeup: `fixing-signing.md`
in the repo root.

Consequences to preserve when touching this code:
- `overlay.x/y/width/height` are **page-local**, in the bitmap's own pixel space (the bitmap is
  always rendered `RenderPdfPageUseCase.RENDER_WIDTH_PX = 1080` px wide). There is no
  scroll-offset concept anymore.
- `item_pdf_page.xml` root is `ConstraintLayout`, not `FrameLayout` — the overlay view is
  constrained to all 4 sides of the `ImageView`, not `match_parent`. A `match_parent` child
  inside a `wrap_content` parent that's measured `UNSPECIFIED` (a normal RecyclerView item) can
  collapse to 0x0.
- Drag/resize only commits to `onOverlaysChanged` once, on `ACTION_UP`/`ACTION_CANCEL` — not on
  every `ACTION_MOVE`. Committing per-move triggers `notifyItemChanged()` on the very item being
  touched, which rebinds the view mid-gesture and kills the touch stream.
- `parent?.requestDisallowInterceptTouchEvent(true)` is called when the overlay captures a drag,
  so it doesn't fight the RecyclerView's own scroll handling.
- All overlay position/size changes are clamped to page bounds in `clampToPage()`.

### 2. Pages render eagerly (all at once), not lazily on scroll

`PdfEditorViewModel.renderAllPages()` renders every page up front (progressively, one page's
bitmap at a time) when a document opens — it does NOT do windowed/lazy rendering (render
current±1, unload distant pages) like the original plan specified. The lazy approach caused a
newly-scrolled-into-view page to flash its skeleton placeholder before the async render
finished, which read as "the signature disappears." Signed documents are typically a handful of
pages, so the extra upfront memory is an accepted trade-off.

The "Add Sign" / "Add Initial" FABs are disabled while `isLoadingPages == true` — adding an
overlay to a page whose bitmap isn't ready yet used to fall back to a hardcoded portrait-shaped
guess, badly wrong for landscape documents.

### 3. Coordinate conversion & page rotation

`PdfCoordinateConverter` (pure functions, unit tested) converts Android pixel space (top-left
origin) to PDF point space (bottom-left origin). `EmbedSignatureToPdfUseCase` also compensates
for pages with a `/Rotate` entry (90/180/270°) — skipping this places overlays in the wrong
coordinate space entirely for rotated pages (width/height swap for 90/270). There's also a
last-resort clamp at the embed step: an out-of-bounds placement gets forced back inside the page
instead of silently drawing off it.

### 4. Fragment Flow collection: `viewLifecycleOwner.lifecycleScope` + `repeatOnLifecycle`

**Do not use** `lifecycleScope.launchWhenStarted { flow.collectLatest {...} }` — `lifecycleScope`
belongs to the Fragment and outlives view recreation (e.g. returning via the back stack).
`launchWhenStarted` blocks from a previous `onViewCreated` never get cancelled, so each time the
Fragment becomes visible again, a new collector stacks on top of the old one. With N stacked
collectors reacting to one event, N side effects fire for a single user action (this caused a
real crash: `IllegalArgumentException` from `NavController.navigate()` being called twice for
one button tap, the second call failing because the destination had already moved).

Always use this pattern instead:
```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { flowA.collectLatest { ... } }
        launch { flowB.collectLatest { ... } }
    }
}
```
`repeatOnLifecycle` is tied to `viewLifecycleOwner`, so it's cleanly cancelled on every
`onDestroyView` and restarted fresh on every `onViewCreated`.

### 5. Pinch-to-zoom is a pure visual transform

`ZoomableContainer` wraps the page `RecyclerView` in `fragment_pdf_editor.xml` and applies
pinch-to-zoom (plus pan while zoomed, double-tap to reset) as `scaleX`/`scaleY`/`translationX`/
`translationY` on the RecyclerView as a whole — one zoom level for the entire document, not per
page. It never touches `SignatureOverlay` coordinates or `SignatureOverlayView` at all: Android
automatically un-transforms touch coordinates delivered to a scaled child, so overlay drag/resize
math is unaffected by zoom level. Disambiguation from an in-progress overlay drag relies entirely
on the `requestDisallowInterceptTouchEvent` call `SignatureOverlayView` already makes when it
captures a touch (see rule 1) — once called, no ancestor's `onInterceptTouchEvent()` fires for the
rest of that gesture, so a second finger touching down mid-drag reaches the overlay's own
`ScaleGestureDetector` (resize) rather than triggering page zoom. Max scale is capped at 3x since
pages are rendered at a fixed `RENDER_WIDTH_PX = 1080` — zooming further reveals no more detail.

### 6. URI handling — always use contentResolver

```kotlin
// CORRECT
val inputStream = context.contentResolver.openInputStream(uri)

// WRONG — throws SecurityException on content:// URIs from external intents / SAF picker
val file = File(uri.path)
```

Also: `uri.lastPathSegment` is **not** a real file name for `content://` URIs from the SAF
picker (it's the provider's internal document ID). Resolve the real display name via
`ContentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), ...)`.

### 7. Cloud Signature Sync — `feature/cloud-signature-sync` branch, not yet merged

Optional login syncs the user's one TTD + one PARAF signature to a separate backend
(`signPDF-Backend`, Go + MySQL, own repo) via a manual Sync button; Guest mode is fully offline
and unchanged (zero network calls). Dev backend: `BuildConfig.API_BASE_URL` points at
`https://signpdf-backend.quezacolt.my.id/` (homelab Cloudflare tunnel -> docker-compose on
`localhost:8090`).

- **`SignPdfApplication` hosts app-wide singletons** (`tokenStore`, `signatureRepository`,
  `authRepository`, `syncRepository`) — the *only* exception to "every ViewModel `new`s its own
  dependencies" (rule at the top of this file). Reason: these hold genuinely global state (one
  login session, one pair of signature files); independently-constructed repository instances
  would each carry their own `StateFlow`, so e.g. a Sync from `HomeViewModel` would update a
  `SignatureRepository` instance that `SignatureViewModel` elsewhere never observes.
- **`LoginViewModel`/`HomeViewModel` use a `@JvmOverloads` constructor** with the repository as a
  default-value parameter (`private val authRepository: AuthRepository = (app as
  SignPdfApplication).authRepository`). This is a testing seam, not a style choice — without
  `@JvmOverloads`, `by viewModels()`'s reflection-based factory (which requires an exact
  `(Application::class.java)` constructor) breaks in production. Tests call the multi-arg
  constructor directly with MockK mocks.
- **`AuthRepository`/`SyncRepository`/`AuthInterceptor`/`ApiClient` depend on the `TokenStorage`
  interface, not concrete `TokenStore`** — `TokenStore` wraps `EncryptedSharedPreferences`, which
  needs Android Keystore (unavailable under Robolectric/JVM tests). Tests use `FakeTokenStorage`.
  `TokenStore` itself has no unit test for this reason.
- **`SyncRepository`'s "dirty" check is inferred from file mtime**
  (`SignatureRepository.lastModifiedAt()` vs `SignatureSlotMeta.localFileModifiedAtMillis`), not
  an explicit flag threaded through `SignatureRepository.saveBitmap()` — deliberate, to keep the
  existing signature-drawing flow (rule 1's overlay code has nothing to do with this, but
  `SignatureViewModel` → `SignatureRepository.saveBitmap()` is the relevant untouched path) free
  of any sync-awareness.
- A `401`/`SESSION_EXPIRED` anywhere calls `AuthRepository.handleSessionExpired()` (clears local
  session, flips to Guest) — it never touches `SignatureRepository`/`SignatureMetadataStore`, so
  an expired session never interrupts an in-progress signing.

---

## Manifest / Permissions

- `READ_EXTERNAL_STORAGE` (maxSdk 32) + `READ_MEDIA_IMAGES`. No `WRITE_EXTERNAL_STORAGE` — output
  is saved to `context.filesDir` only.
- `INTERNET` — added for Cloud Signature Sync (rule 7); Guest mode never triggers a network call.
- `MainActivity`: `launchMode="singleTask"`, two intent-filters (`MAIN`/`LAUNCHER` and
  `ACTION_VIEW` + `mimeType="application/pdf"` for Open With).
- `FileProvider` at `${applicationId}.fileprovider`, paths in `res/xml/file_paths.xml`.
- `android:icon` / `android:roundIcon` point to `@mipmap/ic_launcher` /
  `@mipmap/ic_launcher_round` (generated at 5 densities from the app icon artwork).
- `android:fullBackupContent` / `android:dataExtractionRules` point at
  `res/xml/{backup_rules,data_extraction_rules}.xml`, excluding the entire `sharedpref` domain
  from Auto Backup/device-transfer (the session token must never round-trip through either).
- Theme (`res/values/themes.xml`) is **`Theme.SignPDF`, extending `Theme.Material3.Light.NoActionBar`**
  (migrated from the original `Theme.MaterialComponents.Light.NoActionBar` during a full Material 3
  UI revamp). Still Light-only, not `DayNight`: rendered PDF bitmaps have transparent backgrounds
  where there's no ink; under dark mode that transparency let the dark window background show
  through as black, hiding dark text. Documents should always render on white regardless of the
  device's system theme.

---

## CI/CD

`.github/workflows/build-android.yml` builds a debug APK on every push to `main` or
`release/**` (also `workflow_dispatch`), running unit tests first, uploading the result as
artifact `signpdf-debug-apk`. Runs on GitHub-hosted `ubuntu-latest` — no local Android SDK
needed for this workflow.

`app/debug.keystore` is intentionally committed so every CI build (fresh VM each run) signs
with the same key — otherwise AGP auto-generates a new random debug key per run and every APK
has a different signature, so installing an update always conflicts and requires uninstalling
first. Debug keystore credentials (`androiddebugkey` / `android`) are public/standard, not a
real secret.

---

## Known Limitations

- Pinch-to-zoom max scale is capped at 3x (see rule 5) — a hard limit tied to the fixed 1080px render width.
- Not tested on tablets/large screens or landscape device orientation.
- No instrumented (`androidTest`) tests — `androidTest/` is an empty skeleton. JVM unit tests cover
  `PdfCoordinateConverter` and, on `feature/cloud-signature-sync`, the cloud-sync layer.
- `TokenStore`'s real `EncryptedSharedPreferences`/Android Keystore behavior is untested (see rule 7) —
  no emulator/device available in this dev environment for an instrumented test.
- Cloud Signature Sync (rule 7) is complete and CI-green but not yet merged into `main`/`release/**`.

## Related Docs

- `fixing-signing.md` — deep debugging history for the overlay-placement bug; read before
  touching `SignatureOverlayView` / `PdfPageAdapter` / coordinate conversion code again.
- `CLAUDE.md` — same current-state facts as this file, Indonesian narrative, for Claude Code.
- `README.md` — short overview for new readers/contributors.
