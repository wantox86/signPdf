# GitHub Copilot Instructions — SignPDF Android App

## Agent Behavior Rules

- Do NOT ask clarifying questions. Make decisions based on this document.
- Do NOT wait for confirmation. Execute each task completely.
- If a decision is not specified here, use the most common Android best practice.
- Always write complete, compilable Kotlin code — no placeholders, no `TODO` stubs unless explicitly marked.
- When creating a file, create the full file content, not a partial snippet.
- Follow the directory structure exactly as specified.
- Commit message format: `[SprintN] short description`

---

## Project Identity

| Key | Value |
|---|---|
| App Name | SignPDF |
| Package Name | `com.wantox86.signpdf` |
| Language | Kotlin |
| Build System | Gradle Kotlin DSL (`build.gradle.kts`) |
| Architecture | MVVM + Clean Architecture (UseCase layer) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 |
| Compile SDK | 34 |
| UI Binding | ViewBinding (NOT DataBinding, NOT Jetpack Compose) |
| DI | Hilt |
| Navigation | Navigation Component (single-activity) |
| Async | Kotlin Coroutines + Flow |

---

## Tech Stack — Use Exactly These Libraries

```kotlin
// app/build.gradle.kts — dependencies block

// PDF
implementation("com.tom_roush:pdfbox-android:2.0.27.0")

// Signature Canvas
implementation("com.github.gcacace:signature-pad:0.3.1")

// Image loading
implementation("io.coil-kt:coil:2.6.0")

// Hilt
implementation("com.google.dagger:hilt-android:2.51")
kapt("com.google.dagger:hilt-android-compiler:2.51") // or ksp() if using KSP plugin (preferred for Kotlin 1.9+)

// Lifecycle + ViewModel
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

// Navigation
implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

// UI
implementation("androidx.recyclerview:recyclerview:1.3.2")
implementation("androidx.viewpager2:viewpager2:1.1.0")
implementation("com.google.android.material:material:1.12.0")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")
```

Do NOT add libraries not listed above without explicit instruction.

---

## Directory Structure

Create files at exactly these paths:

```
app/src/main/
├── java/com/wantox86/signpdf/
│   ├── SignPdfApplication.kt
│   ├── MainActivity.kt
│   ├── di/
│   │   └── AppModule.kt
│   ├── domain/
│   │   ├── model/
│   │   │   ├── SignatureSource.kt
│   │   │   ├── SignatureOverlay.kt
│   │   │   └── PdfDocument.kt
│   │   └── usecase/
│   │       ├── EmbedSignatureToPdfUseCase.kt
│   │       ├── RenderPdfPageUseCase.kt
│   │       └── ExportPdfUseCase.kt
│   ├── data/
│   │   ├── PdfRepository.kt
│   │   └── SignatureRepository.kt
│   └── ui/
│       ├── home/
│       │   ├── HomeFragment.kt
│       │   └── HomeViewModel.kt
│       ├── editor/
│       │   ├── PdfEditorFragment.kt
│       │   ├── PdfEditorViewModel.kt
│       │   ├── PdfPageAdapter.kt
│       │   └── SignatureOverlayView.kt
│       ├── signature/
│       │   ├── SignaturePickerBottomSheet.kt
│       │   ├── SignatureCanvasFragment.kt
│       │   └── SignatureViewModel.kt
│       └── share/
│           └── ShareHelper.kt
├── res/
│   ├── layout/
│   │   ├── activity_main.xml
│   │   ├── fragment_home.xml
│   │   ├── fragment_pdf_editor.xml
│   │   ├── fragment_signature_canvas.xml
│   │   ├── item_pdf_page.xml
│   │   └── bottomsheet_signature_picker.xml
│   ├── navigation/
│   │   └── nav_graph.xml
│   └── xml/
│       └── file_paths.xml
└── AndroidManifest.xml
```

---

## Domain Models — Implement Exactly As Specified

### `SignatureSource.kt`
```kotlin
package com.wantox86.signpdf.domain.model

import android.graphics.Bitmap
import android.net.Uri

sealed class SignatureSource {
    data class FromCanvas(val bitmap: Bitmap) : SignatureSource()
    data class FromImageFile(val uri: Uri) : SignatureSource()
}
```

### `SignatureOverlay.kt`
```kotlin
package com.wantox86.signpdf.domain.model

import android.graphics.Bitmap
import java.util.UUID

data class SignatureOverlay(
    val id: String = UUID.randomUUID().toString(),
    val type: OverlayType,
    val bitmap: Bitmap,
    val pageIndex: Int,
    val x: Float,        // immutable — use copy(x = newX) to move
    val y: Float,        // immutable — use copy(y = newY) to move
    val width: Float,    // immutable — use copy(width = newWidth) to resize
    val height: Float,   // immutable — use copy(height = newHeight) to resize
    val createdAt: Long = System.currentTimeMillis()
)

enum class OverlayType { TTD, PARAF }
```

### `PdfDocument.kt`
```kotlin
package com.wantox86.signpdf.domain.model

import android.net.Uri

data class PdfDocument(
    val uri: Uri,
    val fileName: String,
    val pageCount: Int,
    val outputPath: String? = null
)
```

---

## AndroidManifest.xml — Full Spec

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permissions -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <!-- READ_MEDIA_DOCUMENTS does NOT exist in Android API — omit it.
         PDF access via SAF (OpenDocument contract) requires no extra permission. -->

    <application
        android:name=".SignPdfApplication"
        android:allowBackup="true"
        android:label="SignPDF"
        android:theme="@style/Theme.MaterialComponents.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask">

            <!-- Entry point 1: Normal launcher -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Entry point 2: Open PDF from other apps -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:mimeType="application/pdf" />
            </intent-filter>

        </activity>

        <!-- FileProvider for sharing output PDF -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

    </application>
</manifest>
```

---

## Key Implementation Rules

### 1. URI Handling — ALWAYS use contentResolver
```kotlin
// CORRECT — works for content://, file://, all URI schemes
val inputStream = context.contentResolver.openInputStream(uri)
val pdfDoc = PDDocument.load(inputStream)

// WRONG — never do this
val file = File(uri.path) // throws SecurityException on content:// URIs
```

### 2. MainActivity — Handle Both Entry Points
```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        navController = findNavController(R.id.nav_host_fragment)
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val bundle = bundleOf("pdfUri" to intent.data.toString())
            navController.navigate(R.id.pdfEditorFragment, bundle)
        }
    }
}
```

### 3. PDF Coordinate Conversion — ALWAYS apply this formula
```kotlin
// PDF origin = bottom-left. Android origin = top-left.
// Apply this when embedding overlays:
val pdfY = page.mediaBox.height - (overlay.y * scaleY) - (overlay.height * scaleY)
val pdfX = overlay.x * scaleX
// scaleX = page.mediaBox.width / renderedBitmapWidth
// scaleY = page.mediaBox.height / renderedBitmapHeight
```

### 4. EmbedSignatureToPdfUseCase — Core Logic
```kotlin
suspend fun execute(document: PdfDocument, overlays: List<SignatureOverlay>): File =
    withContext(Dispatchers.IO) {
        val pdfDoc = PDDocument.load(
            context.contentResolver.openInputStream(document.uri)
        )
        // Use the same constant as RenderPdfPageUseCase.RENDER_WIDTH_PX to guarantee coordinate accuracy
        val renderedWidth = RenderPdfPageUseCase.RENDER_WIDTH_PX.toFloat()

        overlays.groupBy { it.pageIndex }.forEach { (pageIndex, pageOverlays) ->
            val page = pdfDoc.getPage(pageIndex)
            val scaleX = page.mediaBox.width / renderedWidth
            val renderedHeight = renderedWidth * (page.mediaBox.height / page.mediaBox.width)
            val scaleY = page.mediaBox.height / renderedHeight

            val contentStream = PDPageContentStream(
                pdfDoc, page,
                PDPageContentStream.AppendMode.APPEND, true, true
            )
            pageOverlays.forEach { overlay ->
                val pdImage = LosslessFactory.createFromImage(pdfDoc, overlay.bitmap)
                val pdfX = overlay.x * scaleX
                val pdfY = page.mediaBox.height - (overlay.y * scaleY) - (overlay.height * scaleY)
                contentStream.drawImage(pdImage, pdfX, pdfY, overlay.width * scaleX, overlay.height * scaleY)
            }
            contentStream.close()
        }

        val outputFile = File(context.filesDir, "signed_${document.fileName}")
        pdfDoc.save(outputFile)
        pdfDoc.close()
        outputFile
    }
```

### 5. Transparent Bitmap from SignaturePad
```kotlin
// Use gcacace SignaturePad's built-in method
val bitmap: Bitmap = signaturePad.transparentSignatureBitmap
// This returns ARGB_8888 Bitmap with white strokes on transparent background
```

### 6. SignatureOverlayView — Drag & Resize
- Extend `View`
- Store list of `SignatureOverlay` objects
- On `ACTION_DOWN`: find which overlay was touched (hit test by x/y bounds)
- On `ACTION_MOVE`: update selected overlay's `x`, `y`
- Use `ScaleGestureDetector` for pinch → update `width`, `height`
- Draw dashed border + corner handle on selected overlay
- Draw "×" button at top-right of selected overlay; tap it to remove

### 7. ShareHelper
```kotlin
fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan PDF via"))
}
```

### 8. Bitmap Memory — Render per page only
```kotlin
// In RenderPdfPageUseCase — do NOT render all pages at once
companion object {
        // Must match the width used in EmbedSignatureToPdfUseCase for correct coordinate mapping
        const val RENDER_WIDTH_PX = 1080
    }

    suspend fun execute(document: PdfDocument, pageIndex: Int): Bitmap =
        withContext(Dispatchers.IO) {
            val pdfDoc = PDDocument.load(
                context.contentResolver.openInputStream(document.uri)
            )
            val page = pdfDoc.getPage(pageIndex)
            // Calculate DPI from actual page width — works for A4, Letter, Legal, and custom sizes
            val pageWidthInches = page.mediaBox.width / 72f // PDF points → inches (1 pt = 1/72 in)
            val dpi = RENDER_WIDTH_PX / pageWidthInches
            val renderer = PDFRenderer(pdfDoc)
            val bitmap = renderer.renderImageWithDPI(pageIndex, dpi, Bitmap.Config.ARGB_8888)
            pdfDoc.close()
            bitmap
        }
```

---

## Sprint Execution Plan

Execute one sprint at a time. Do not start the next sprint until all tasks in current sprint are complete and the app compiles without errors.

---

### Sprint 1 — Foundation

**Goal:** App opens, PDF renders page by page, Open With works.

Tasks — create these files in order:

1. `app/build.gradle.kts` — full dependencies as specified above
2. `AndroidManifest.xml` — full manifest as specified above
3. `res/xml/file_paths.xml`:
   ```xml
   <paths>
       <files-path name="signed_pdfs" path="." />
       <cache-path name="cache" path="." />
   </paths>
   ```
4. `SignPdfApplication.kt` — annotate with `@HiltAndroidApp`
5. `di/AppModule.kt` — provide `Context` via Hilt
6. `domain/model/PdfDocument.kt` — as specified
7. `domain/usecase/RenderPdfPageUseCase.kt` — as specified
8. `data/PdfRepository.kt` — wrap RenderPdfPageUseCase
9. `res/navigation/nav_graph.xml` — HomeFragment (startDestination) → PdfEditorFragment, arg: `pdfUri: String`
10. `res/layout/activity_main.xml` — NavHostFragment fullscreen
11. `MainActivity.kt` — as specified (handleIncomingIntent)
12. `res/layout/fragment_home.xml` — single "Buka PDF" button, centered
13. `ui/home/HomeViewModel.kt` — expose `openPdf(uri: Uri)` event via `SharedFlow`
14. `ui/home/HomeFragment.kt` — FilePicker via `ActivityResultContracts.OpenDocument(arrayOf("application/pdf"))`, navigate to editor on result
15. `res/layout/item_pdf_page.xml` — single `ImageView` match_parent
16. `ui/editor/PdfPageAdapter.kt` — `RecyclerView.Adapter`, bind `Bitmap` to ImageView
17. `res/layout/fragment_pdf_editor.xml` — `RecyclerView` (vertical) + FAB "Tambah TTD" + FAB "Tambah Paraf"
18. `ui/editor/PdfEditorViewModel.kt` — load PDF, expose `pages: StateFlow<List<Bitmap>>`, `overlays: StateFlow<List<SignatureOverlay>>`
19. `ui/editor/PdfEditorFragment.kt` — receive `pdfUri` arg, render pages into RecyclerView

**Definition of Done Sprint 1:**
- App installs and launches
- Tapping "Buka PDF" opens file picker
- Selecting a PDF renders all pages as scrollable list
- Opening a PDF from Files app or Gmail shows app in chooser, tapping it opens the PDF directly in editor

---

### Sprint 2 — Signature Input

**Goal:** User can draw TTD/Paraf on canvas OR import from image file. Result saved as transparent Bitmap.

Tasks:

1. `domain/model/SignatureSource.kt` — as specified
2. `data/SignatureRepository.kt` — store current TTD bitmap and PARAF bitmap in memory (StateFlow)
3. `ui/signature/SignatureViewModel.kt` — expose `saveTtd(bitmap)`, `saveParaf(bitmap)`, `importFromUri(uri, type)`
4. `res/layout/fragment_signature_canvas.xml` — `SignaturePad` view full screen + "Clear" button + "Confirm" button
5. `ui/signature/SignatureCanvasFragment.kt` — on Confirm: call `signaturePad.transparentSignatureBitmap`, save result via `SignatureViewModel.savePendingBitmap(bitmap, type)` (do NOT pass Bitmap via Bundle/setFragmentResult — Bitmap is too large and will crash)
   - Navigate back after saving
6. `res/layout/bottomsheet_signature_picker.xml` — two options: "Gambar di Layar" and "Import Gambar", plus label showing TTD or Paraf
7. `ui/signature/SignaturePickerBottomSheet.kt` — receive `overlayType: OverlayType` arg
   - "Gambar di Layar" → call `setFragmentResult("signature_picker", bundleOf("action" to "canvas", "type" to overlayType.name))`, then dismiss
   - "Import Gambar" → call `setFragmentResult("signature_picker", bundleOf("action" to "import", "type" to overlayType.name))`, then dismiss
   - `PdfEditorFragment` listens via `setFragmentResultListener("signature_picker")` and handles navigation/image picker from there
8. Wire image picker: `ActivityResultContracts.GetContent("image/*")` → load bitmap via Coil preserving alpha → save via SignatureViewModel

**Definition of Done Sprint 2:**
- Tapping "Tambah TTD" opens BottomSheet
- Drawing on canvas and confirming saves a transparent TTD bitmap
- Importing a PNG with transparent background preserves transparency

---

### Sprint 3 — Overlay & Drag

**Goal:** TTD/Paraf bitmaps appear as draggable, resizable overlays on the PDF page. Multiple overlays supported.

Tasks:

1. `domain/model/SignatureOverlay.kt` — as specified
2. `ui/editor/SignatureOverlayView.kt` — custom View:
   - Receives list of `SignatureOverlay` via public method `setOverlays(list)`
   - Draws each overlay bitmap at its x/y/width/height
   - On touch: select overlay under finger
   - On move: drag selected overlay
   - `ScaleGestureDetector`: resize selected overlay
   - Draw dashed rect border + corner handle on selected
   - Draw "×" at top-right of selected; tap removes it
   - Exposes changes via `onOverlaysChanged: (List<SignatureOverlay>) -> Unit` callback (do NOT use StateFlow in a View)
   - ViewModel sets this callback and updates its own StateFlow on changes
3. Update `res/layout/fragment_pdf_editor.xml` — add `SignatureOverlayView` as overlay on top of RecyclerView (FrameLayout parent)
4. Update `PdfEditorViewModel`:
   - Add `addOverlay(type: OverlayType, bitmap: Bitmap, pageIndex: Int)`
   - Default position: center of current page
   - Default size: 30% of rendered page width × proportional height (e.g., `width = renderedPageWidth * 0.30f`, `height = width * 0.35f`) — do NOT hardcode pixel values
   - Observe overlay changes from `SignatureOverlayView`
5. Update `PdfEditorFragment`:
   - FAB "Tambah TTD" → open `SignaturePickerBottomSheet(OverlayType.TTD)`
   - FAB "Tambah Paraf" → open `SignaturePickerBottomSheet(OverlayType.PARAF)`
   - Receive result via `setFragmentResultListener`, call `viewModel.addOverlay(...)`

**Definition of Done Sprint 3:**
- After drawing TTD, overlay appears on current page
- Overlay can be dragged freely
- Overlay can be resized with pinch
- Tapping × removes the overlay
- Both TTD and Paraf can exist simultaneously on same page

---

### Sprint 4 — Embed & Share

**Goal:** PDF with embedded TTD/Paraf can be saved and shared to other apps.

Tasks:

1. `domain/usecase/EmbedSignatureToPdfUseCase.kt` — as specified (with coordinate conversion)
2. `domain/usecase/ExportPdfUseCase.kt` — orchestration layer: calls `EmbedSignatureToPdfUseCase`, then updates `PdfDocument.outputPath` with the result path, and returns output `File`. Keeps ViewModel clean from PDF I/O details.
3. `ui/share/ShareHelper.kt` — as specified
4. Add "Simpan & Share" button to `fragment_pdf_editor.xml`
5. Update `PdfEditorViewModel` — add `exportAndShare()` suspend function:
   - Call `EmbedSignatureToPdfUseCase`
   - Expose `exportState: StateFlow<ExportState>` (Idle / Loading / Success(file) / Error)
6. Update `PdfEditorFragment` — observe `exportState`:
   - Loading → show progress dialog
   - Success → call `ShareHelper.sharePdf(context, file)`
   - Error → show Snackbar with error message

**Definition of Done Sprint 4:**
- Tapping "Simpan & Share" embeds all overlays into PDF
- Android share sheet appears
- PDF can be opened in WhatsApp, Telegram, Gmail, Teams
- Overlays appear at correct position and size in shared PDF

---

### Sprint 5 — Polish & Edge Cases

Tasks:

1. Multi-page: ensure overlays are per-page (already in model), verify embed handles all pages correctly
2. Undo/redo: add `undoOverlay()` and `redoOverlay()` to ViewModel
   - Use `ArrayDeque<List<SignatureOverlay>>` as history stack (snapshot of entire overlay list per action)
   - On every add/move/resize/delete: push current list snapshot to undo stack, clear redo stack
   - `undoOverlay()`: pop from undo stack → push current to redo stack → restore snapshot
   - `redoOverlay()`: pop from redo stack → push current to undo stack → restore snapshot
3. Add undo/redo buttons to editor toolbar
4. Loading skeleton while PDF pages render (show placeholder in RecyclerView)
5. Error handling:
   - PDF fails to load → show error dialog with message, stay on Home
   - PDF has 0 pages → show "File PDF tidak valid"
   - Export fails → show Snackbar "Gagal menyimpan PDF"
6. Memory: recycle Bitmap pages that are far from current scroll position (RecyclerView recycling)
7. Test on Android 8.0 (API 26) and Android 14 (API 34) — fix any API-level issues

**Definition of Done Sprint 5:**
- App stable across Android 8–14
- Corrupt PDF shows error, does not crash
- Undo/redo works correctly
- Large PDFs (50+ pages) do not cause OOM

---

## Critical Constraints — Never Violate

| Rule | Detail |
|---|---|
| No `file://` URI | Always use `contentResolver.openInputStream(uri)` |
| No `WRITE_EXTERNAL_STORAGE` | Save output to `context.filesDir` only |
| Coordinate conversion | Always convert Android (top-left) → PDF (bottom-left) when embedding |
| Bitmap config | Always `Bitmap.Config.ARGB_8888` for transparency support |
| Render on demand | Never render all PDF pages at once — render per page in `RenderPdfPageUseCase` |
| FileProvider only | Never use raw `file://` URI for sharing — always wrap with `FileProvider.getUriForFile()` |
| `launchMode="singleTask"` | Required on MainActivity to prevent duplicate instances when opening PDFs |

---

## Progress Log

### Sprint 1 — Foundation (Completed 2026-05-26)
- Status: DONE
- Build check: `./gradlew assembleDebug` passed
- Unit test check: `./gradlew testDebugUnitTest` passed
- Notes:
    - Gradle wrapper Java detection fixed in `gradlew`.
    - Added missing `gradle.properties` (`android.useAndroidX=true`).
    - Added `local.properties` SDK path for local environment.
    - Updated dependency coordinates to resolvable artifacts:
        - `com.tom-roush:pdfbox-android:2.0.27.0`
        - `com.github.gcacace:signature-pad:1.3.1`

### Sprint 2 — Signature Input (Completed 2026-05-26)
- Status: DONE
- Build check: `./gradlew assembleDebug` passed
- Unit test check: `./gradlew testDebugUnitTest` passed
- Notes:
    - Added `SignatureSource`, `SignatureRepository`, and `SignatureViewModel` for in-memory signature state.
    - Implemented `SignatureCanvasFragment` using `transparentSignatureBitmap`.
    - Implemented `SignaturePickerBottomSheet` with fragment result contract (`signature_picker`).
    - Wired image import flow in editor using `ActivityResultContracts.GetContent("image/*")` + Coil.
    - Added upfront runtime permission request in `MainActivity` (API-aware: `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`).

### Sprint 3 — Overlay & Drag (Completed 2026-05-26)
- Status: DONE
- Build check: `./gradlew assembleDebug` passed
- Unit test check: `./gradlew testDebugUnitTest` passed
- Notes:
    - Added immutable `SignatureOverlay` model and `OverlayType` enum usage in editor flow.
    - Implemented `SignatureOverlayView` custom view with hit test, drag, pinch resize, dashed selection border, corner handle, and delete `x` control.
    - Added overlay layer on top of PDF RecyclerView in editor layout.
    - Updated `PdfEditorViewModel` with `addOverlay()` and `updateOverlays()`.
    - Connected signature result flow from canvas/import to overlay insertion in `PdfEditorFragment`.

### Sprint 4 — Embed & Share (Completed 2026-05-26)
- Status: DONE
- Build check: `./gradlew assembleDebug` passed
- Unit test check: `./gradlew testDebugUnitTest` passed
- Notes:
    - Implemented `EmbedSignatureToPdfUseCase` with Android-to-PDF coordinate conversion and scale mapping.
    - Added `ExportPdfUseCase` to orchestrate embed result and update output path on `PdfDocument`.
    - Implemented `ShareHelper` using `FileProvider` + `Intent.ACTION_SEND`.
    - Added `Simpan & Share` button in editor and wired export flow in `PdfEditorViewModel` with `ExportState` (Idle/Loading/Success/Error).
    - Added loading dialog, success share trigger, and error Snackbar handling in `PdfEditorFragment`.
