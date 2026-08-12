# CLAUDE.md — SignPDF (Android PDF Digital Signature Editor)

> Dokumen ini nunjukin **kondisi project SEKARANG** (bukan rencana awal). Rencana awal
> (sprint plan, tech stack rencana dengan Hilt, dll) udah banyak berubah selama development —
> kalau nemu dokumen lama yang nyebut Hilt atau "render on-demand per halaman", itu **udah
> nggak berlaku**, dianggap basi. Baca file ini buat ground truth.

## Project Overview

**Nama:** SignPDF
**Package:** `com.wantox86.signpdf`
**Platform:** Android, min SDK 26 (Android 8.0), target & compile SDK 34
**Bahasa:** Kotlin
**Build System:** Gradle Kotlin DSL
**Arsitektur:** MVVM + UseCase layer, **tanpa DI framework** (lihat "Keputusan Arsitektur" di bawah)

Aplikasi buat buka, menandatangani (digital), dan membagikan dokumen PDF dari HP. User bisa nambahin **Sign** (tanda tangan) dan **Initial** (paraf) — istilah UI dalam Bahasa Inggris, tapi secara internal kode masih pakai `OverlayType.TTD` / `OverlayType.PARAF` (nama enum nggak diubah, cuma label yang ditampilkan ke user yang di-translate — lihat `R.string.overlay_type_ttd`/`overlay_type_paraf`).

---

## Tech Stack (aktual, bukan rencana)

| Komponen | Library / Tool | Versi |
|---|---|---|
| PDF render & manipulasi | `com.tom-roush:pdfbox-android` | 2.0.27.0 |
| Signature canvas | `com.github.gcacace:signature-pad` | 1.3.1 |
| Image loading | `io.coil-kt:coil` | 2.6.0 |
| Navigasi | AndroidX Navigation Component | 2.7.7 |
| ViewModel/Lifecycle | `androidx.lifecycle:*-ktx` | 2.8.0 |
| Coroutines | `kotlinx-coroutines-android` | 1.8.0 |
| UI | ViewBinding + ConstraintLayout (bukan Compose) | — |
| AGP / Kotlin / Gradle | 8.3.2 / 1.9.23 / 8.6 | — |

**DI: TIDAK ADA.** Hilt sempat dipasang di rencana awal (`@HiltAndroidApp`, `AppModule`, `kapt`), tapi di-drop total — nggak ada satupun ViewModel yang beneran pakai `@Inject`, semua dependency (`PdfRepository`, `EmbedSignatureToPdfUseCase`, dst) di-construct manual di constructor ViewModel (`private val pdfRepository = PdfRepository(app)`). Kalau mau nambah dependency baru, ikutin pola manual-construct ini, jangan pasang ulang Hilt kecuali app-nya beneran udah butuh (dependency graph lebih kompleks dari sekarang).

---

## Struktur Direktori (aktual)

```
app/src/main/java/com/wantox86/signpdf/
├── SignPdfApplication.kt        # PDFBoxResourceLoader.init() + CrashHandler.install()
├── CrashHandler.kt              # global uncaught exception handler, tulis stack trace ke file,
│                                 # ditampilkan di dialog copyable pas launch berikutnya (MainActivity)
├── MainActivity.kt              # single activity, host NavController, handle intent ACTION_VIEW (Open With)
├── data/
│   ├── PdfRepository.kt         # wrap RenderPdfPageUseCase
│   └── SignatureRepository.kt   # simpen TTD/Paraf yang di-save user (persist ke filesDir/signatures/*.png)
├── domain/
│   ├── model/
│   │   ├── PdfDocument.kt       # uri, fileName, pageCount, outputPath
│   │   └── SignatureOverlay.kt  # id, type (OverlayType), bitmap, pageIndex, x/y/width/height, createdAt
│   ├── usecase/
│   │   ├── RenderPdfPageUseCase.kt      # render 1 halaman PDF -> Bitmap, lebar tetap RENDER_WIDTH_PX=1080
│   │   ├── EmbedSignatureToPdfUseCase.kt # embed semua overlay ke PDF asli, handle rotasi halaman + clamp bounds
│   │   └── ExportPdfUseCase.kt          # orkestrasi: panggil embed, update PdfDocument.outputPath
│   └── util/
│       └── PdfCoordinateConverter.kt    # murni matematika konversi koordinat, ada unit test-nya
└── ui/
    ├── home/                     # HomeFragment + HomeViewModel — entry point, file picker
    ├── editor/
    │   ├── PdfEditorFragment.kt      # layar utama: viewer + overlay Sign/Initial
    │   ├── PdfEditorViewModel.kt
    │   ├── PdfPageAdapter.kt         # RecyclerView adapter, overlay per-halaman (lihat di bawah)
    │   └── SignatureOverlayView.kt   # custom View drag/resize, SEKARANG per-halaman (bukan global)
    ├── signature/                # SignatureCanvasFragment, SignaturePickerBottomSheet, SignatureViewModel
    ├── preview/                  # PdfPreviewFragment + PdfPreviewViewModel — review sebelum share
    └── share/
        └── ShareHelper.kt

app/src/test/java/.../domain/util/PdfCoordinateConverterTest.kt   # satu-satunya unit test yang ada
```

Nggak ada `SignatureSource.kt` (sempat direncanakan di awal — sealed class `FromCanvas`/`FromImageFile` — tapi nggak pernah kepake di kode, udah dihapus) dan nggak ada `di/AppModule.kt` (Hilt module, juga dihapus).

---

## Keputusan Arsitektur Penting

### 1. Overlay per halaman, bukan satu view global

`SignatureOverlayView` adalah **child dari tiap item `item_pdf_page.xml`** (satu instance per halaman RecyclerView), BUKAN satu view fullscreen yang numpuk di atas seluruh list kayak desain awal. Ini perubahan besar setelah ditemukan bug serius: desain lama nentuin "halaman aktif" pakai `findFirstVisibleItemPosition()`, jadi kalau user naruh Sign di halaman yang secara visual ada di bawah (saat 2 halaman kelihatan sebagian pas scroll), koordinatnya kesimpen relatif ke halaman yang salah — bisa berujung ke-embed di luar batas kertas (invisible di hasil akhir), dan overlay "hilang-muncul" tiap kali first-visible-page berubah pas scroll.

Konsekuensi desain sekarang:
- `overlay.x/y/width/height` selalu page-local murni (relatif ke bitmap halaman itu sendiri), nggak ada lagi konsep scroll-offset.
- `SignatureOverlayView` di dalam item punya `setPageBitmapSize(width, height)` — dipanggil tiap `bind()` — buat ngitung `scale()` (rasio ukuran View on-screen vs ukuran bitmap asli 1080px lebar), dipakai buat convert antara koordinat touch (layar) dan koordinat tersimpan (bitmap).
- Layout item pakai **ConstraintLayout** (bukan FrameLayout) dengan constraint eksplisit overlay-view ke 4 sisi ImageView — kalau pakai `match_parent` biasa di dalam parent `wrap_content` yang diukur `UNSPECIFIED` (kondisi normal RecyclerView item), overlay view bisa collapse ke ukuran 0x0.
- Drag/resize **cuma commit ke luar (`onOverlaysChanged`) sekali pas gesture selesai** (`ACTION_UP`/`ACTION_CANCEL`), bukan tiap `ACTION_MOVE` — commit per-gerakan bikin RecyclerView `notifyItemChanged()` di item yang lagi disentuh, rebind view di tengah gesture, touch stream putus. Perubahan visual selama drag di-invalidate lokal doang.
- `requestDisallowInterceptTouchEvent` dipanggil pas overlay nangkep drag, biar nggak "tarik-tarikan" sama scroll RecyclerView.
- Semua perubahan overlay di-clamp ke batas halaman (`clampToPage()` di `SignatureOverlayView`) — nggak bisa digeser/resize sampai keluar kertas.

Detail lengkap investigasi & analisis bug ini ada di **`fixing-signing.md`** (root file repo) — worth dibaca kalau nyentuh area overlay lagi.

### 2. Render semua halaman upfront (eager), bukan lazy per-scroll

`PdfEditorViewModel.renderAllPages()` render SEMUA halaman sekaligus (progresif, emit tiap 1 halaman kelar) pas dokumen dibuka — bukan windowed/lazy (render currentPage±1, unload yang jauh) kayak desain awal. Pendekatan lazy dulu bikin halaman yang baru kelihatan pas discroll sempet nunjukin skeleton dulu (async gap), yang keliatan kayak "overlay hilang muncul". Trade-off: pakai lebih banyak memori upfront, dianggap wajar karena dokumen yang ditandatangani biasanya nggak sampai ratusan halaman.

Tombol "Add Sign"/"Add Initial" **disabled selama render masih jalan** (`isLoadingPages == true`) — nyegah user nambah overlay ke halaman yang bitmap-nya belum ada (dulu ini nyebabin fallback ke ukuran tebakan potrait yang salah buat dokumen landscape).

### 3. Konversi koordinat & rotasi halaman

`PdfCoordinateConverter` (murni fungsi matematika, ada unit test) handle konversi piksel-Android (top-left origin) ke points-PDF (bottom-left origin). `EmbedSignatureToPdfUseCase` juga handle halaman yang punya `/Rotate` (90/180/270°) — kalau kelewat, overlay bisa ke-embed di ruang koordinat yang salah (lebar/tinggi ketuker buat rotasi 90/270). Ada juga safety-net clamp terakhir di titik embed: kalau somehow ada overlay yang keitung out-of-bounds, dipaksa masuk ke dalam kertas daripada diem-diem invisible.

### 4. Collector Flow di Fragment: `viewLifecycleOwner.lifecycleScope` + `repeatOnLifecycle`

**JANGAN** pakai `lifecycleScope.launchWhenStarted { flow.collectLatest {...} }` (lifecycleScope milik Fragment, bukan View). Semua Fragment (`HomeFragment`, `PdfEditorFragment`, `PdfPreviewFragment`) pakai pola:
```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { flowA.collectLatest { ... } }
        launch { flowB.collectLatest { ... } }
    }
}
```
Alasan: `lifecycleScope` Fragment **bertahan lintas re-create view** (misal balik dari back stack) — `launchWhenStarted` lama nggak pernah ke-cancel, jadi tiap kali Fragment kelihatan lagi, collector BARU numpuk di atas yang lama. Efeknya: satu event (klik tombol, hasil signature) diproses berkali-kali oleh collector yang menumpuk — pernah nyebabin crash nyata (`IllegalArgumentException: Navigation action ... cannot be found from the current destination`, karena navigate() ke-panggil 2x dari 2 collector buat 1 event yang sama). `repeatOnLifecycle` terikat `viewLifecycleOwner`, otomatis cancel bersih tiap `onDestroyView`.

### 5. CI/CD & signing

Push ke `main`/`release/**` trigger `.github/workflows/build-android.yml` (GitHub Actions, `ubuntu-latest`) — build APK debug, jalanin unit test dulu, upload sebagai artifact `signpdf-debug-apk`. **Nggak butuh Android SDK lokal** buat dev di lingkungan tanpa Android Studio — verifikasi lokal cukup pakai `./gradlew tasks` (cek config valid), compile beneran divalidasi CI.

`app/debug.keystore` **sengaja di-commit** ke repo (bukan kebocoran — kredensial keystore debug memang publik/standar: alias `androiddebugkey`, password `android`). Kalau nggak di-commit, tiap build CI (VM fresh tiap run) generate keystore baru dengan key acak, bikin tiap APK beda signature, dan install APK baru selalu ke-reject ("signature conflict") kecuali uninstall dulu.

### 6. Crash handler & diagnostic

`CrashHandler.install()` (dipanggil di `SignPdfApplication.onCreate()`) pasang `Thread.setDefaultUncaughtExceptionHandler` global — nulis stack trace crash terakhir ke file lokal, ditampilin di dialog copyable (`TextView.setTextIsSelectable(true)` di dalam `ScrollView`) pas `MainActivity` launch berikutnya. Berguna buat debug di device tanpa `adb`/logcat.

---

## Permissions & Manifest

- `READ_EXTERNAL_STORAGE` (maxSdk 32) + `READ_MEDIA_IMAGES` — buat baca gambar TTD/paraf yang di-import.
- **Nggak ada** `WRITE_EXTERNAL_STORAGE` — output PDF disimpan ke `context.filesDir` (internal storage app), nggak butuh permission tulis eksternal.
- `MainActivity` — `launchMode="singleTask"`, dua intent-filter: `MAIN`/`LAUNCHER` (buka normal) dan `ACTION_VIEW` + `mimeType="application/pdf"` (Open With dari app lain — File Manager, Gmail, WhatsApp, Drive, browser). URI dari intent eksternal selalu `content://`, **jangan pernah** `File(uri.path)` — selalu `contentResolver.openInputStream(uri)`.
- `FileProvider` terdaftar (`${applicationId}.fileprovider`) buat share PDF hasil ke app lain, path config di `res/xml/file_paths.xml`.
- `android:icon`/`android:roundIcon` — mipmap `ic_launcher`/`ic_launcher_round` di 5 density, digenerate dari artwork icon app.

Nama file PDF hasil export: query display name asli dari `ContentResolver` (`OpenableColumns.DISPLAY_NAME`) — **jangan** pakai `uri.lastPathSegment` (itu document ID internal SAF provider, bukan nama file, nggak ada ekstensi yang bener). Format nama hasil: `<nama_asli>_signed.pdf`.

---

## Keterbatasan yang diketahui

- Belum ada zoom in/out di halaman PDF (cuma fit-width, `ImageView` `scaleType="fitCenter"`).
- Belum di-test di tablet/layar besar atau orientasi landscape device (fokus testing selama ini di HP portrait).
- Cuma ada 1 unit test (`PdfCoordinateConverterTest`) — belum ada instrumented test (`androidTest`).

## Referensi lain

- `fixing-signing.md` (root repo) — analisis mendalam & riwayat debugging bug penempatan overlay (kalau nyentuh `SignatureOverlayView`/`PdfPageAdapter`/koordinat lagi, baca ini dulu).
- `.github/copilot-instructions.md` — instruksi buat GitHub Copilot, harus tetap konsisten sama file ini kalau ada perubahan arsitektur.
- `README.md` — overview singkat buat pembaca baru/kontributor.
