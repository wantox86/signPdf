# SignPDF

An Android application for opening, digitally signing, and sharing PDF documents directly from a mobile device, without the traditional print-sign-scan workflow. Users can add a **Sign** (signature) and an **Initial** to any page, drag and resize their placement, and share the result to any app that accepts PDFs (WhatsApp, Telegram, Gmail, etc.).

## Features

- **Open a PDF** from the system file picker, or directly via the "Open With" menu in other apps (Files, Gmail, WhatsApp, Drive, browsers, etc.)
- **Sign & Initial**: draw directly on screen, import from a PNG/JPG image (transparent background preserved), or reuse a previously saved signature
- **Drag and resize** freely per page; multiple signatures/initials can be placed across different pages simultaneously
- **Undo/redo** per action (add, move, resize, delete)
- **Preview** the final result before sharing, auto-scrolling to the first signed page
- **Share** to other applications via the standard Android share sheet

## Tech Stack

| Component | Library |
|---|---|
| Language | Kotlin |
| PDF rendering & manipulation | [`com.tom-roush:pdfbox-android`](https://github.com/TomRoush/PdfBox-Android) |
| Signature canvas | [`com.github.gcacace:signature-pad`](https://github.com/gcacace/android-signaturepad) |
| Image loading | [`io.coil-kt:coil`](https://coil-kt.github.io/coil/) |
| Navigation | AndroidX Navigation Component (single-activity) |
| UI | ViewBinding + ConstraintLayout (no Jetpack Compose) |
| Asynchrony | Kotlin Coroutines + Flow |
| Architecture | MVVM + UseCase layer (no dependency-injection framework — see notes below) |

Minimum SDK 26 (Android 8.0), target and compile SDK 34.

## Build & Run

### Via CI (no local Android Studio/SDK required)

Every push to the `main` or `release/**` branch is automatically built by GitHub Actions (`.github/workflows/build-android.yml`), producing a ready-to-install debug APK available from this repository's **Actions** tab (artifact `signpdf-debug-apk`). The workflow can also be triggered manually via `workflow_dispatch`.

The debug keystore used for signing is committed to the repository (`app/debug.keystore`) intentionally — this is not a credential leak, as debug keystore credentials are public and standard by convention. Committing it ensures every CI-built APK is signed identically, so installing an updated build never conflicts with (or requires uninstalling) a previous one.

### Locally (requires the Android SDK)

```bash
./gradlew assembleDebug   # build a debug APK -> app/build/outputs/apk/debug/
./gradlew test            # run unit tests
```

## Project Structure

```
app/src/main/java/com/wantox86/signpdf/
├── SignPdfApplication.kt        # initializes PDFBoxResourceLoader and the global crash handler
├── CrashHandler.kt              # catches crashes, shows the last stack trace in a copyable dialog on next launch
├── MainActivity.kt              # single activity, hosts the NavController, handles "Open With" intents
├── data/                        # repositories (PdfRepository, SignatureRepository)
├── domain/
│   ├── model/                   # PdfDocument, SignatureOverlay
│   ├── usecase/                 # RenderPdfPageUseCase, EmbedSignatureToPdfUseCase, ExportPdfUseCase
│   └── util/                    # PdfCoordinateConverter (with unit tests)
└── ui/
    ├── home/                    # HomeFragment — entry point, opens the file picker
    ├── editor/                  # PdfEditorFragment — main screen: viewer + Sign/Initial overlays
    ├── signature/               # SignatureCanvasFragment, SignaturePickerBottomSheet
    ├── preview/                 # PdfPreviewFragment — review the result before sharing
    └── share/                   # ShareHelper
```

## Notable Architectural Decisions

- **Overlays are rendered per page, not by a single global view.** `SignatureOverlayView` is a child of each RecyclerView page item, rather than one fullscreen view stacked above the entire list. This was a deliberate fix following a serious bug in which signatures could be placed on the wrong page and end up outside the document's visible bounds. Full analysis is documented in `fixing-signing.md`.
- **All pages are rendered eagerly** rather than lazily on scroll. Documents being signed are typically only a few pages long, so the additional upfront memory cost is an acceptable trade-off for scrolling that never shows a rendering gap.
- **Overlay coordinates** (`SignatureOverlay.x/y/width/height`) are always expressed in the page bitmap's own pixel space (not screen pixels), and are converted to PDF units (points, bottom-left origin) via `PdfCoordinateConverter` at embed time.
- **No dependency-injection framework is used.** Hilt was set up initially but later removed, as no ViewModel ever actually used `@Inject` — every dependency is constructed manually in its constructor. This is sufficient for an application of this size.
- **Pinch-to-zoom is a pure visual transform, decoupled from overlay data.** `ZoomableContainer` wraps the page `RecyclerView` and applies `scaleX/scaleY`/`translationX/translationY` to it as a whole (one zoom level for the entire document, not per page). It never touches `SignatureOverlay` coordinates, and correctly defers to an in-progress overlay drag via Android's standard `requestDisallowInterceptTouchEvent` mechanism — no changes were needed in `SignatureOverlayView` to support this.
- **Flow collection in Fragments uses `viewLifecycleOwner.lifecycleScope` with `repeatOnLifecycle`**, not the deprecated `lifecycleScope.launchWhenStarted`. A Fragment's own `lifecycleScope` survives view recreation (e.g. returning via the back stack), so `launchWhenStarted` blocks never get cancelled and collectors accumulate — this previously caused a duplicate-navigation crash. Follow the existing pattern in `PdfEditorFragment`/`PdfPreviewFragment`/`HomeFragment` when adding new collectors.

## Known Limitations

- Not tested on tablets, large screens, or landscape device orientation.
- No instrumented (`androidTest`) tests exist yet — only a unit test for `PdfCoordinateConverter`.

## License

MIT — see [LICENSE](LICENSE).
