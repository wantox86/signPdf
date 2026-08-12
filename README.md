# SignPDF

Aplikasi Android buat buka, tanda tangan (digital), dan bagikan dokumen PDF langsung dari HP — tanpa perlu print-scan-print. Tambahin **Sign** (tanda tangan) dan **Initial** (paraf) ke halaman mana pun, geser & resize posisinya, lalu share hasilnya ke WhatsApp/Telegram/Gmail/apa aja yang bisa nerima PDF.

## Fitur

- **Buka PDF** dari file picker, atau langsung lewat menu "Open With" di app lain (Files, Gmail, WhatsApp, Drive, browser, dll)
- **Sign & Initial**: gambar langsung di layar, import dari gambar PNG/JPG (background transparan dipertahankan), atau pakai yang udah pernah disimpan sebelumnya
- **Drag & resize** bebas per halaman, bisa naruh beberapa Sign/Initial sekaligus di halaman berbeda
- **Undo/redo** per aksi (nambah, geser, resize, hapus)
- **Preview** hasil akhir sebelum di-share, auto-scroll ke halaman yang ditandatangani
- **Share** ke aplikasi lain via Android share sheet standar

## Tech stack

| Komponen | Library |
|---|---|
| Bahasa | Kotlin |
| Render & manipulasi PDF | [`com.tom-roush:pdfbox-android`](https://github.com/TomRoush/PdfBox-Android) |
| Signature canvas | [`com.github.gcacace:signature-pad`](https://github.com/gcacace/android-signaturepad) |
| Image loading | [`io.coil-kt:coil`](https://coil-kt.github.io/coil/) |
| Navigasi | AndroidX Navigation Component (single-activity) |
| UI | ViewBinding + ConstraintLayout (bukan Compose) |
| Async | Kotlin Coroutines + Flow |
| Arsitektur | MVVM + UseCase layer (bukan pakai DI framework — lihat catatan di bawah) |

Min SDK 26 (Android 8.0), target & compile SDK 34.

## Build & run

### Lewat CI (nggak butuh Android Studio/SDK lokal)

Tiap push ke branch `main` atau `release/**` otomatis di-build sama GitHub Actions (`.github/workflows/build-android.yml`) — hasilnya APK debug siap install, bisa didownload dari tab **Actions** repo ini (artifact `signpdf-debug-apk`). Bisa juga di-trigger manual lewat `workflow_dispatch`.

Keystore debug yang dipakai udah di-commit (`app/debug.keystore`) — **sengaja**, bukan kebocoran kredensial (kredensial keystore debug memang publik/standar), tujuannya biar tiap build APK dari CI konsisten pakai signature yang sama, jadi update install nggak pernah conflict/minta uninstall dulu.

### Lokal (butuh Android SDK)

```bash
./gradlew assembleDebug   # build APK debug -> app/build/outputs/apk/debug/
./gradlew test            # jalanin unit test
```

## Struktur project

```
app/src/main/java/com/wantox86/signpdf/
├── SignPdfApplication.kt        # init PDFBoxResourceLoader + global crash handler
├── CrashHandler.kt              # tangkep crash, tampilin stack trace di dialog copyable pas next launch
├── MainActivity.kt              # single activity, host NavController, handle "Open With"
├── data/                        # Repository (PdfRepository, SignatureRepository)
├── domain/
│   ├── model/                   # PdfDocument, SignatureOverlay
│   ├── usecase/                 # RenderPdfPageUseCase, EmbedSignatureToPdfUseCase, ExportPdfUseCase
│   └── util/                    # PdfCoordinateConverter (+ unit test)
└── ui/
    ├── home/                    # HomeFragment — entry point, buka file picker
    ├── editor/                  # PdfEditorFragment — halaman utama: viewer + overlay Sign/Initial
    ├── signature/                # SignatureCanvasFragment, SignaturePickerBottomSheet
    ├── preview/                  # PdfPreviewFragment — review hasil sebelum share
    └── share/                    # ShareHelper
```

## Catatan arsitektur penting

- **Overlay per halaman, bukan satu view global.** `SignatureOverlayView` adalah child dari tiap item `RecyclerView` (satu instance per halaman), bukan satu view fullscreen yang numpuk di atas semua halaman. Ini keputusan sadar setelah bug lama (overlay salah tempat/ilang pas scroll) — detail lengkap penyebab & analisisnya ada di `fixing-signing.md`.
- **Render semua halaman upfront**, bukan lazy per-scroll. Dokumen yang ditandatangani biasanya cuma beberapa halaman, jadi trade-off pakai lebih banyak memori di awal demi scroll yang nggak pernah nunjukin gap/skeleton itu sepadan buat use-case ini.
- **Koordinat overlay** (`SignatureOverlay.x/y/width/height`) selalu dalam ruang piksel bitmap halaman (bukan piksel layar) — dikonversi ke satuan PDF (points, origin bottom-left) lewat `PdfCoordinateConverter` pas proses embed.
- **Nggak pakai DI framework.** Hilt sempat dipasang di awal tapi di-drop — nggak ada satupun ViewModel yang benar-benar pakai `@Inject`, semua dependency di-construct manual di constructor. Untuk app sekecil ini, itu udah cukup.
- **Collector Flow di Fragment pakai `viewLifecycleOwner.lifecycleScope` + `repeatOnLifecycle`**, bukan `lifecycleScope.launchWhenStarted` biasa. `lifecycleScope` milik Fragment bertahan lintas re-create view (misal balik dari back stack), jadi `launchWhenStarted` lama nggak pernah ke-cancel dan collector numpuk — pernah nyebabin crash navigasi dobel-trigger. Ikutin pola yang sudah ada di `PdfEditorFragment`/`PdfPreviewFragment`/`HomeFragment` kalau nambah collector baru.

## Keterbatasan yang diketahui

- Belum ada zoom in/out di halaman PDF (cuma fit-width).
- Preview & editor belum di-test di tablet/layar besar atau orientasi landscape device.
- Belum ada test instrumented (`androidTest`) — cuma unit test buat `PdfCoordinateConverter`.

## Lisensi

MIT — lihat [LICENSE](LICENSE).
