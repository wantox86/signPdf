# CLAUDE.md — Android PDF Digital Signature Editor

## Project Overview

**Nama Aplikasi:** SignPDF (nama sementara, bisa disesuaikan)
**Platform:** Android (min SDK 26 / Android 8.0+)
**Bahasa:** Kotlin
**Build System:** Gradle (Kotlin DSL)
**Architecture:** MVVM + Clean Architecture (UseCase layer)

Aplikasi Android untuk membuka, mengedit, dan menandatangani dokumen PDF secara digital.
Pengguna dapat menambahkan **tanda tangan (TTD)** dan **paraf** ke dokumen PDF, baik dari gambar yang sudah ada (PNG/JPG transparan) maupun dengan menggambar langsung di layar. Hasil PDF dapat dibagikan ke aplikasi lain seperti WhatsApp, Teams, Telegram, dan lainnya.

---

## Tech Stack

| Komponen | Library / Tool | Keterangan |
|---|---|---|
| PDF Rendering | `com.tom_roush:pdfbox-android` | Render & manipulasi PDF |
| PDF Embed Signature | `com.tom_roush:pdfbox-android` | Embed gambar TTD ke PDF |
| Signature Canvas | `com.github.gcacace:signature-pad` atau custom `View` | Menggambar TTD di layar |
| Image Loading | `io.coil-kt:coil` | Load gambar TTD dari file |
| Background Removal | Manual (PNG transparan sudah cukup) | User sudah siapkan PNG no-bg |
| File Picker | `ActivityResultContracts.GetContent` | Pilih file PDF & gambar |
| Share | `FileProvider` + `Intent.ACTION_SEND` | Share PDF ke apps lain |
| DI | `Hilt` | Dependency Injection |
| Navigation | `Navigation Component` | Single-activity navigation |
| ViewModel | `androidx.lifecycle:lifecycle-viewmodel-ktx` | State management |
| Coroutines | `kotlinx-coroutines-android` | Async processing |
| UI | `ViewBinding` + `ConstraintLayout` | Layout |
| Storage | `Scoped Storage` + `FileProvider` | Android 8+ compliant |

---

## Fitur Utama

### 1. TTD (Tanda Tangan)
- **Sumber TTD:**
  - Gambar langsung di layar (`SignaturePad` / custom `Canvas`)
  - Import dari file gambar (PNG/JPG tanpa background)
- **Penempatan:**
  - Drag-and-drop bebas di atas halaman PDF
  - Resize dengan pinch gesture
  - Konfirmasi posisi sebelum embed
- **Output:** Ditanam (embed) ke PDF sebagai gambar transparan

### 2. Paraf
- Fitur identik dengan TTD namun terpisah secara slot/label
- Bisa punya source paraf berbeda dari TTD
- Bisa ditempel di posisi berbeda, bisa berulang di banyak halaman

### 3. PDF Viewer
- Render tiap halaman PDF sebagai `Bitmap`
- Swipe antar halaman (ViewPager2 atau RecyclerView horizontal)
- Zoom in/out per halaman
- Overlay layer untuk posisi TTD/paraf di atas halaman

### 4. Open With (Intent Filter)
- Apps terdaftar sebagai PDF handler di sistem Android
- Muncul di chooser saat user tap file PDF dari File Manager, Gmail, WhatsApp, Google Drive, Browser, dll
- PDF langsung terbuka di editor tanpa perlu buka apps dulu
- Support URI scheme `content://` dan `file://`

### 5. Export & Share
- Simpan PDF hasil ke penyimpanan internal app
- Share ke aplikasi lain via `Intent.ACTION_SEND` + `FileProvider`
- Target: WhatsApp, Telegram, Teams, Gmail, dan semua apps yang support PDF

---

## Struktur Direktori

```
app/
├── src/main/
│   ├── java/com/example/signpdf/
│   │   ├── MainActivity.kt
│   │   ├── di/
│   │   │   └── AppModule.kt
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── SignatureSource.kt       # sealed class: Canvas | ImageFile
│   │   │   │   ├── SignatureOverlay.kt      # data: type, bitmap, x, y, w, h, page
│   │   │   │   └── PdfDocument.kt
│   │   │   └── usecase/
│   │   │       ├── EmbedSignatureToPdfUseCase.kt
│   │   │       ├── RenderPdfPageUseCase.kt
│   │   │       └── ExportPdfUseCase.kt
│   │   ├── data/
│   │   │   ├── PdfRepository.kt
│   │   │   └── SignatureRepository.kt
│   │   └── ui/
│   │       ├── home/
│   │       │   ├── HomeFragment.kt          # Open PDF, pilih file
│   │       │   └── HomeViewModel.kt
│   │       ├── editor/
│   │       │   ├── PdfEditorFragment.kt     # Main editor: viewer + overlay TTD
│   │       │   ├── PdfEditorViewModel.kt
│   │       │   ├── PdfPageAdapter.kt        # RecyclerView adapter untuk halaman
│   │       │   └── SignatureOverlayView.kt  # Custom View: drag/resize overlay
│   │       ├── signature/
│   │       │   ├── SignaturePickerBottomSheet.kt  # Pilih: Gambar di layar vs Import
│   │       │   ├── SignatureCanvasFragment.kt     # Menggambar TTD/paraf
│   │       │   └── SignatureViewModel.kt
│   │       └── share/
│   │           └── ShareHelper.kt
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml
│   │   │   ├── fragment_home.xml
│   │   │   ├── fragment_pdf_editor.xml
│   │   │   ├── fragment_signature_canvas.xml
│   │   │   ├── item_pdf_page.xml
│   │   │   └── bottomsheet_signature_picker.xml
│   │   └── xml/
│   │       └── file_paths.xml               # FileProvider paths
│   └── AndroidManifest.xml
```

---

## Model Data

### `SignatureSource.kt`
```kotlin
sealed class SignatureSource {
    data class FromCanvas(val bitmap: Bitmap) : SignatureSource()
    data class FromImageFile(val uri: Uri) : SignatureSource()
}
```

### `SignatureOverlay.kt`
```kotlin
data class SignatureOverlay(
    val id: String = UUID.randomUUID().toString(),
    val type: OverlayType,         // TTD atau PARAF
    val bitmap: Bitmap,
    val pageIndex: Int,
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    val createdAt: Long = System.currentTimeMillis()
)

enum class OverlayType { TTD, PARAF }
```

### `PdfDocument.kt`
```kotlin
data class PdfDocument(
    val uri: Uri,
    val fileName: String,
    val pageCount: Int,
    val outputPath: String? = null
)
```

---

## Alur Utama (User Flow)

```
[Entry Point 1 — Manual]
[Home Screen]
    → Tap "Buka PDF" → File Picker → PDF terbuka di Editor

[Entry Point 2 — Open With / Intent Filter]
[Apps lain: File Manager, Gmail, WhatsApp, Drive, Browser]
    → User tap file PDF → Android chooser muncul
    → User pilih "SignPDF" → MainActivity menerima Intent.ACTION_VIEW
    → Langsung navigasi ke PdfEditorFragment (skip Home)
    
[PDF Editor]
    → Halaman di-render satu per satu (RecyclerView/ViewPager2)
    → Tap "Tambah TTD" atau "Tambah Paraf"
        → BottomSheet: pilih "Gambar di Layar" atau "Import Gambar"
            → [Gambar di Layar] → SignatureCanvasFragment
                → User menggambar → Simpan sebagai Bitmap transparan
            → [Import Gambar] → FilePicker (PNG/JPG)
                → Bitmap di-load, background transparan dipertahankan
        → Overlay TTD/Paraf muncul di halaman aktif
        → User drag ke posisi yang diinginkan
        → User pinch untuk resize
        → Tap "Confirm" → overlay terkunci (bisa di-edit ulang)
    
    → Tap "Simpan & Share"
        → EmbedSignatureToPdfUseCase: semua overlay di-render ke PDF asli
        → PDF output disimpan ke internal storage
        → ShareHelper: Intent.ACTION_SEND → pilih apps tujuan
```

---

## Use Case: Embed Signature ke PDF

### `EmbedSignatureToPdfUseCase.kt`
```kotlin
// Pseudocode alur embed
suspend fun execute(document: PdfDocument, overlays: List<SignatureOverlay>): File {
    val pdfDoc = PDDocument.load(context.contentResolver.openInputStream(document.uri))

    overlays.groupBy { it.pageIndex }.forEach { (pageIndex, pageOverlays) ->
        val page = pdfDoc.getPage(pageIndex)
        val contentStream = PDPageContentStream(pdfDoc, page, APPEND, true, true)

        pageOverlays.forEach { overlay ->
            val pdImage = LosslessFactory.createFromImage(pdfDoc, overlay.bitmap)
            // Koordinat PDF berbasis bottom-left, perlu konversi dari top-left Android
            val pdfY = page.mediaBox.height - overlay.y - overlay.height
            contentStream.drawImage(pdImage, overlay.x, pdfY, overlay.width, overlay.height)
        }
        contentStream.close()
    }

    val outputFile = File(context.filesDir, "signed_${document.fileName}")
    pdfDoc.save(outputFile)
    pdfDoc.close()
    return outputFile
}
```

> **Penting:** Koordinat Android (top-left origin) harus dikonversi ke koordinat PDF (bottom-left origin) saat embed.
> Rasio antara pixel layar dan unit PDF (points) juga perlu diperhitungkan berdasarkan skala render.

---

## Signature Canvas

### Behavior `SignatureCanvasFragment`
- Canvas putih dengan border subtle
- Tombol **Clear** untuk hapus ulang
- Tombol **Confirm** untuk simpan sebagai Bitmap dengan background transparan
- Gunakan `Canvas.drawPath()` atau library `signature-pad`
- Saat konfirmasi: crop area non-kosong, set background `Color.TRANSPARENT`

### Hasil Bitmap Transparan
```kotlin
fun extractTransparentBitmap(canvas: SignaturePad): Bitmap {
    val original = canvas.transparentSignatureBitmap // dari lib gcacace
    // Atau manual: buat Bitmap ARGB_8888, gambar path di atasnya
    return original
}
```

---

## SignatureOverlayView (Drag & Resize)

Custom `View` yang di-overlay di atas halaman PDF:

- **Drag:** `ACTION_MOVE` touch event → update `x`, `y`
- **Resize:** Pinch gesture via `ScaleGestureDetector` → update `width`, `height`
- **Multi overlay:** support multiple overlay sekaligus per halaman, masing-masing independen
- **Handle UI:** tampilkan border dashed + handle di pojok saat overlay dipilih
- **Delete:** tombol "×" kecil di pojok kanan atas overlay saat dipilih

---

## Intent Filter — Open With

### `AndroidManifest.xml` (di dalam `<activity>` MainActivity)
```xml
<!-- Daftarkan apps sebagai PDF handler -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:mimeType="application/pdf" />
</intent-filter>
```

Setelah ini, apps akan muncul di chooser ketika user membuka PDF dari:
- File Manager (Files by Google, Cx File Explorer, dll)
- Gmail / Outlook — attachment PDF
- WhatsApp / Telegram — dokumen PDF yang diterima
- Google Drive — open dengan apps lain
- Browser — PDF hasil download

### Handling Intent di `MainActivity.kt`
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    handleIncomingIntent(intent)
}

override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    // Handle jika apps sudah terbuka (singleTask launchMode)
    intent?.let { handleIncomingIntent(it) }
}

private fun handleIncomingIntent(intent: Intent) {
    if (intent.action == Intent.ACTION_VIEW) {
        val pdfUri: Uri? = intent.data
        if (pdfUri != null) {
            // Langsung buka editor, skip Home screen
            val bundle = bundleOf("pdfUri" to pdfUri.toString())
            navController.navigate(R.id.pdfEditorFragment, bundle)
        }
    }
}
```

### Tambahkan `launchMode` di `AndroidManifest.xml`
```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTask"
    ...>
```

> `singleTask` penting agar jika apps sudah terbuka, intent baru masuk via `onNewIntent` bukan membuat instance baru.

### Handling URI `content://` di `PdfEditorViewModel`
```kotlin
// URI dari Intent eksternal selalu content://, bukan file://
// Gunakan contentResolver, BUKAN File(uri.path)
val inputStream = context.contentResolver.openInputStream(pdfUri)
val pdfDocument = PDDocument.load(inputStream)
```

> Jangan pernah asumsikan URI adalah `file://`. Selalu pakai `contentResolver.openInputStream(uri)`.

---

## FileProvider Setup

### `AndroidManifest.xml`
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### `res/xml/file_paths.xml`
```xml
<paths>
    <files-path name="signed_pdfs" path="." />
    <cache-path name="cache" path="." />
</paths>
```

---

## Share PDF

### `ShareHelper.kt`
```kotlin
fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan PDF via"))
}
```

---

## Permissions

### `AndroidManifest.xml`
```xml
<!-- Baca file PDF & gambar dari storage -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_DOCUMENTS" />
```

> Tidak perlu `WRITE_EXTERNAL_STORAGE` karena output disimpan di internal storage app (`filesDir`).

---

## Gradle Dependencies (app/build.gradle.kts)

```kotlin
dependencies {
    // PDF
    implementation("com.tom_roush:pdfbox-android:2.0.27.0")

    // Signature Canvas
    implementation("com.github.gcacace:signature-pad:0.3.1")

    // Image loading
    implementation("io.coil-kt:coil:2.6.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-android-compiler:2.51")

    // Lifecycle / ViewModel / Coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // UI
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
```

---

## Sprint Plan

### Sprint 1 — Foundation (1 minggu)
- [ ] Setup project: Kotlin, Hilt, Navigation Component
- [ ] HomeFragment: open file PDF via FilePicker
- [ ] Intent Filter: daftarkan apps sebagai PDF handler (`ACTION_VIEW`)
- [ ] `MainActivity.handleIncomingIntent()`: langsung ke editor jika dibuka via Open With
- [ ] PdfEditorFragment: render halaman PDF sebagai Bitmap (pdfbox-android)
- [ ] RecyclerView/ViewPager2 untuk scroll antar halaman

### Sprint 2 — Signature Input (1 minggu)
- [ ] SignaturePickerBottomSheet: pilih Canvas atau Import
- [ ] SignatureCanvasFragment: gambar TTD/paraf, simpan Bitmap transparan
- [ ] Import gambar PNG/JPG dari storage, pertahankan transparansi
- [ ] SignatureViewModel: simpan hasil TTD & paraf terpisah

### Sprint 3 — Overlay & Drag (1 minggu)
- [ ] SignatureOverlayView: render overlay TTD/paraf di atas halaman
- [ ] Implementasi drag (touch move)
- [ ] Implementasi resize (pinch ScaleGestureDetector)
- [ ] Multi-overlay support (TTD dan paraf bisa keduanya ada)
- [ ] Tombol hapus per overlay

### Sprint 4 — Embed & Share (1 minggu)
- [ ] EmbedSignatureToPdfUseCase: overlay → PDF via pdfbox-android
- [ ] Koordinat konversi Android → PDF
- [ ] FileProvider setup
- [ ] ShareHelper: share PDF ke aplikasi lain
- [ ] Tes end-to-end: WhatsApp, Telegram, Teams, Gmail

### Sprint 5 — Polish & Edge Cases (1 minggu)
- [ ] Handling PDF multi-halaman: TTD di satu halaman, paraf di halaman lain
- [ ] Undo/redo overlay
- [ ] Preview sebelum share
- [ ] Loading state & error handling (PDF corrupt, file terlalu besar)
- [ ] Dukungan Android 8–14

---

## Catatan Penting

1. **Koordinat PDF vs Android:** PDFBox menggunakan sistem koordinat bottom-left (origin di bawah). Android menggunakan top-left. Rumus konversi: `pdfY = pageHeight - androidY - overlayHeight`. Skala juga perlu disesuaikan antara pixel layar dan PDF points (1 point = 1/72 inch).

2. **Transparansi Bitmap:** Pastikan Bitmap dibuat dengan `Bitmap.Config.ARGB_8888`. Saat embed ke PDF via `LosslessFactory`, transparansi PNG dipertahankan.

3. **Memori:** Halaman PDF yang di-render sebagai Bitmap bisa besar. Gunakan `BitmapFactory.Options.inSampleSize` atau render per halaman saja (bukan semua sekaligus).

4. **pdfbox-android vs iText:** pdfbox-android gratis (Apache 2.0). iText memerlukan lisensi komersial untuk produksi. Gunakan pdfbox-android.

5. **Background Removal:** Aplikasi tidak melakukan background removal otomatis. User diasumsikan sudah menyiapkan PNG transparan. Jika ingin menambahkan fitur auto-remove background, pertimbangkan ML Kit atau library `rembg` via Python microservice (out of scope MVP).

7. **Intent Filter & Open With:** Tambahkan `<intent-filter>` dengan `ACTION_VIEW` + `mimeType="application/pdf"` di `AndroidManifest.xml`. Set `launchMode="singleTask"` di Activity agar tidak double instance. Handle incoming URI di `onCreate` dan `onNewIntent`.

8. **Jangan gunakan `file://` URI:** URI yang masuk via Intent eksternal selalu `content://`. Mengakses via `File(uri.path)` akan gagal atau throw `SecurityException`. Selalu gunakan `contentResolver.openInputStream(uri)`.

