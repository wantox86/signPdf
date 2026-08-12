# CLAUDE.md — SignPDF (Android PDF Digital Signature Editor)

> This document describes the project's **current state**, not its original plan. Substantial
> changes were made during development — if you encounter an older reference mentioning Hilt or
> "on-demand per-page rendering," treat it as outdated. This file is the source of truth.

## Project Overview

**Name:** SignPDF
**Package:** `com.wantox86.signpdf`
**Platform:** Android, minimum SDK 26 (Android 8.0), target and compile SDK 34
**Language:** Kotlin
**Build System:** Gradle Kotlin DSL
**Architecture:** MVVM with a UseCase layer, **without a dependency-injection framework** (see "Architectural Decisions" below)

An application for opening, digitally signing, and sharing PDF documents from a mobile device. Users can add a **Sign** (signature) and an **Initial** — these are the user-facing English labels; internally, the code still uses `OverlayType.TTD` / `OverlayType.PARAF` (the enum names were not renamed, only the displayed labels were translated — see `R.string.overlay_type_ttd` / `overlay_type_paraf`).

---

## Tech Stack (Actual, Not Planned)

| Component | Library / Tool | Version |
|---|---|---|
| PDF rendering & manipulation | `com.tom-roush:pdfbox-android` | 2.0.27.0 |
| Signature canvas | `com.github.gcacace:signature-pad` | 1.3.1 |
| Image loading | `io.coil-kt:coil` | 2.6.0 |
| Navigation | AndroidX Navigation Component | 2.7.7 |
| ViewModel/Lifecycle | `androidx.lifecycle:*-ktx` | 2.8.0 |
| Coroutines | `kotlinx-coroutines-android` | 1.8.0 |
| UI | ViewBinding + ConstraintLayout (no Compose) | — |
| AGP / Kotlin / Gradle | 8.3.2 / 1.9.23 / 8.6 | — |

**No dependency-injection framework is used.** Hilt was configured in the original plan (`@HiltAndroidApp`, `AppModule`, `kapt`) but was subsequently removed in full — no ViewModel ever actually used `@Inject`; every dependency (`PdfRepository`, `EmbedSignatureToPdfUseCase`, etc.) is constructed manually in the ViewModel constructor (`private val pdfRepository = PdfRepository(app)`). New dependencies should follow this manual-construction pattern; do not reintroduce Hilt unless the dependency graph genuinely outgrows manual wiring.

---

## Directory Structure (Actual)

```
app/src/main/java/com/wantox86/signpdf/
├── SignPdfApplication.kt        # PDFBoxResourceLoader.init() + CrashHandler.install()
├── CrashHandler.kt              # global uncaught-exception handler; writes the stack trace to a
│                                 # file, shown as a copyable dialog on the next launch (MainActivity)
├── MainActivity.kt              # single activity, hosts the NavController, handles ACTION_VIEW intents (Open With)
├── data/
│   ├── PdfRepository.kt         # wraps RenderPdfPageUseCase
│   └── SignatureRepository.kt   # persists user-saved TTD/Paraf bitmaps (to filesDir/signatures/*.png)
├── domain/
│   ├── model/
│   │   ├── PdfDocument.kt       # uri, fileName, pageCount, outputPath
│   │   └── SignatureOverlay.kt  # id, type (OverlayType), bitmap, pageIndex, x/y/width/height, createdAt
│   ├── usecase/
│   │   ├── RenderPdfPageUseCase.kt       # renders one PDF page to a Bitmap; fixed width RENDER_WIDTH_PX = 1080
│   │   ├── EmbedSignatureToPdfUseCase.kt # embeds all overlays into the original PDF; handles page rotation and bounds clamping
│   │   └── ExportPdfUseCase.kt           # orchestration: invokes the embed step, updates PdfDocument.outputPath
│   └── util/
│       └── PdfCoordinateConverter.kt     # pure coordinate-conversion math, covered by a unit test
└── ui/
    ├── home/                     # HomeFragment + HomeViewModel — entry point, file picker
    ├── editor/
    │   ├── PdfEditorFragment.kt      # main screen: PDF viewer + Sign/Initial overlays
    │   ├── PdfEditorViewModel.kt
    │   ├── PdfPageAdapter.kt         # RecyclerView adapter; overlays are rendered per page (see below)
    │   ├── SignatureOverlayView.kt   # custom drag/resize View, now instantiated per page (not globally)
    │   └── ZoomableContainer.kt      # pinch-to-zoom + pan wrapper around the page RecyclerView
    ├── signature/                # SignatureCanvasFragment, SignaturePickerBottomSheet, SignatureViewModel
    ├── preview/                  # PdfPreviewFragment + PdfPreviewViewModel — review before sharing
    └── share/
        └── ShareHelper.kt

app/src/test/java/.../domain/util/PdfCoordinateConverterTest.kt   # the only unit test in the repository
```

There is no `SignatureSource.kt` (planned early — a sealed class with `FromCanvas`/`FromImageFile` variants — but never actually used in the codebase, and subsequently removed) and no `di/AppModule.kt` (the Hilt module, removed along with Hilt).

---

## Key Architectural Decisions

### 1. Overlays are rendered per page, not by a single global view

`SignatureOverlayView` is a **child of each `item_pdf_page.xml` item** (one instance per RecyclerView page), rather than a single fullscreen view stacked above the entire list, as in the original design. This was a significant change following the discovery of a serious bug: the original design determined the "current page" using `findFirstVisibleItemPosition()`, so if a user placed a Sign on a page that was visually the lower of two partially visible pages during scrolling, its coordinates were stored relative to the wrong page — which could result in the signature being embedded outside the page's visible bounds (invisible in the final output), and overlays appearing to vanish whenever the first-visible page changed during scrolling.

Consequences of the current design:
- `overlay.x/y/width/height` are always purely page-local (relative to that page's own bitmap); there is no longer any scroll-offset concept.
- The `SignatureOverlayView` inside each item has `setPageBitmapSize(width, height)`, called on every `bind()`, which computes a `scale()` factor (the ratio between the view's on-screen size and the actual 1080px-wide source bitmap), used to convert between touch coordinates (screen space) and stored coordinates (bitmap space).
- Item layouts use **ConstraintLayout** (not FrameLayout), with the overlay view explicitly constrained to all four edges of the `ImageView`. A plain `match_parent` child inside a `wrap_content` parent measured `UNSPECIFIED` (the normal condition for a RecyclerView item) can collapse to a size of 0x0.
- Drag/resize changes are **only committed externally (via `onOverlaysChanged`) once the gesture ends** (`ACTION_UP`/`ACTION_CANCEL`), not on every `ACTION_MOVE`. Committing on every move causes RecyclerView's `notifyItemChanged()` to rebind the touched item mid-gesture, breaking the touch stream. Visual feedback during the drag is handled via a local `invalidate()` only.
- `requestDisallowInterceptTouchEvent` is called when an overlay captures a drag, to prevent it from competing with the RecyclerView's own scroll handling.
- All overlay changes are clamped to the page's bounds (`clampToPage()` in `SignatureOverlayView`) — an overlay cannot be dragged or resized outside the page.

A full write-up of this investigation is available in **`fixing-signing.md`** (repository root) — worth reading before modifying anything in this area again.

### 2. All pages are rendered eagerly, not lazily on scroll

`PdfEditorViewModel.renderAllPages()` renders every page at once (progressively, emitting each page's bitmap as it completes) when a document is opened, rather than the original windowed/lazy approach (rendering the current page ±1, unloading distant pages). The lazy approach caused a newly-scrolled-into-view page to briefly show its skeleton placeholder before the asynchronous render completed, which read as "the signature disappears." This trade-off — higher upfront memory usage — is accepted as reasonable given that signed documents are typically not hundreds of pages long.

The "Add Sign"/"Add Initial" buttons are **disabled while rendering is in progress** (`isLoadingPages == true`), preventing overlays from being added to a page whose bitmap does not yet exist (this previously caused a fallback to an incorrect guessed portrait size, which was badly wrong for landscape documents).

### 3. Coordinate conversion and page rotation

`PdfCoordinateConverter` (pure functions, unit tested) handles converting Android pixel coordinates (top-left origin) to PDF point coordinates (bottom-left origin). `EmbedSignatureToPdfUseCase` also compensates for pages carrying a `/Rotate` entry (90/180/270°) — omitting this places overlays in the wrong coordinate space entirely for rotated pages (width and height are swapped for 90°/270° rotations). A last-resort clamp is applied at the embed step as well: any overlay computed to be out of bounds is forced back within the page rather than silently rendered off it.

### 4. Fragment Flow collection: `viewLifecycleOwner.lifecycleScope` with `repeatOnLifecycle`

**Do not** use `lifecycleScope.launchWhenStarted { flow.collectLatest {...} }` (a Fragment's `lifecycleScope`, as opposed to its view's). All Fragments (`HomeFragment`, `PdfEditorFragment`, `PdfPreviewFragment`) follow this pattern:
```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { flowA.collectLatest { ... } }
        launch { flowB.collectLatest { ... } }
    }
}
```
Rationale: a Fragment's own `lifecycleScope` **survives view recreation** (e.g. returning via the back stack) — a previous `launchWhenStarted` block is never cancelled, so every time the Fragment becomes visible again, a new collector stacks on top of the old one. The effect is that a single event (a button tap, a signature result) gets processed multiple times by the accumulated collectors — this previously caused a real crash (`IllegalArgumentException: Navigation action ... cannot be found from the current destination`, because `navigate()` was invoked twice by two collectors for a single event). `repeatOnLifecycle` is tied to `viewLifecycleOwner`, so it is cleanly cancelled on every `onDestroyView`.

### 5. CI/CD and signing

A push to `main` or `release/**` triggers `.github/workflows/build-android.yml` (GitHub Actions, `ubuntu-latest`) — it builds a debug APK, runs unit tests first, and uploads the result as artifact `signpdf-debug-apk`. **No local Android SDK is required** for development in environments without Android Studio; local verification is limited to `./gradlew tasks` (validates configuration only), with actual compilation verified by CI.

`app/debug.keystore` is **intentionally committed** to the repository — this is not a credential leak (debug keystore credentials are public and standard by convention: alias `androiddebugkey`, password `android`). Without this, every CI build (a fresh VM per run) would generate a new keystore with a random key, giving each APK a different signature and causing every install to be rejected as a "signature conflict" unless the previous build was uninstalled first.

### 6. Crash handler and diagnostics

`CrashHandler.install()` (invoked from `SignPdfApplication.onCreate()`) installs a global `Thread.setDefaultUncaughtExceptionHandler` — it writes the last crash's stack trace to a local file, which is shown as a copyable dialog (`TextView.setTextIsSelectable(true)` inside a `ScrollView`) the next time `MainActivity` launches. This is useful for debugging on a device without `adb`/logcat access.

### 7. Pinch-to-zoom is a pure visual transform

`ZoomableContainer` (`ui/editor/ZoomableContainer.kt`) wraps the page `RecyclerView` in `fragment_pdf_editor.xml` and applies pinch-to-zoom (plus pan while zoomed, and double-tap to reset) as `scaleX`/`scaleY`/`translationX`/`translationY` on the RecyclerView as a whole — one zoom level for the entire document, not per page. It deliberately never touches `SignatureOverlay` coordinates or `SignatureOverlayView` at all: zoom is purely a rendering-level transform, and Android automatically un-transforms touch coordinates delivered to a scaled child, so overlay drag/resize math is completely unaffected regardless of zoom level.

Disambiguation between a page-zoom gesture and an overlay drag/resize relies entirely on the standard `requestDisallowInterceptTouchEvent` mechanism that `SignatureOverlayView` already calls when it captures a touch on a selected overlay (see decision #1) — once called, Android will not invoke `onInterceptTouchEvent()` on any ancestor (including `ZoomableContainer`) for the rest of that gesture, so a second finger touching down mid-drag is guaranteed to reach the overlay's own `ScaleGestureDetector` (for resize) rather than triggering page zoom. No changes were needed in `SignatureOverlayView` to support this.

Known edge case: if the RecyclerView has already begun consuming a single-finger vertical scroll (which itself calls `requestDisallowInterceptTouchEvent` per standard nested-scrolling behavior) before a second finger touches down, `ZoomableContainer` will not get a chance to intercept for that gesture. This only affects the uncommon case of starting a scroll and only then deciding to pinch; a normal two-finger-pinch-from-the-start gesture is unaffected.

---

## Permissions and Manifest

- `READ_EXTERNAL_STORAGE` (maxSdk 32) + `READ_MEDIA_IMAGES` — for reading imported signature images.
- **No** `WRITE_EXTERNAL_STORAGE` — output PDFs are saved to `context.filesDir` (the app's internal storage), which requires no external write permission.
- `MainActivity` — `launchMode="singleTask"`, with two intent filters: `MAIN`/`LAUNCHER` (normal launch) and `ACTION_VIEW` + `mimeType="application/pdf"` (Open With, from apps such as File Manager, Gmail, WhatsApp, Drive, or a browser). URIs from external intents are always `content://` — **never** use `File(uri.path)`; always use `contentResolver.openInputStream(uri)`.
- A `FileProvider` is registered (`${applicationId}.fileprovider`) for sharing the resulting PDF with other apps; its path configuration is in `res/xml/file_paths.xml`.
- `android:icon`/`android:roundIcon` — `ic_launcher`/`ic_launcher_round` mipmaps at five densities, generated from the app's icon artwork.

The exported PDF's file name is resolved via a `ContentResolver` query for the real display name (`OpenableColumns.DISPLAY_NAME`) — **not** `uri.lastPathSegment` (which is the SAF provider's internal document ID, not a file name, and carries no usable extension). The output naming format is `<original name>_signed.pdf`.

---

## Known Limitations

- Pinch-to-zoom max scale is capped at 3x (`ZoomableContainer.MAX_SCALE`); since pages are always rendered at a fixed `RENDER_WIDTH_PX = 1080`, zooming beyond that reveals no additional detail and would only look pixelated.
- Not tested on tablets, large screens, or landscape device orientation (testing to date has focused on portrait-mode phones).
- Only one unit test exists (`PdfCoordinateConverterTest`); no instrumented (`androidTest`) tests have been added.

## Related Documentation

- `fixing-signing.md` (repository root) — an in-depth analysis and debugging history of the overlay-placement bug (read this before modifying `SignatureOverlayView`, `PdfPageAdapter`, or coordinate-conversion code again).
- `.github/copilot-instructions.md` — instructions for GitHub Copilot; should be kept consistent with this file whenever the architecture changes.
- `README.md` — a short overview for new readers and contributors.
