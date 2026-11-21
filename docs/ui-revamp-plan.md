# SignPDF — UI Revamp Plan & Handoff ("Ink & Paper" Design System)

- **Branch:** `release/v1.1.0` (increment dari tag `v1.0.0`, `versionCode = 2`, `versionName = "1.1.0"`)
- **Referensi Prototype:** `https://share.gemini.google/MqDHY4URNUE5` (*SignPDF — UI Revamp Concept · Ink & Paper Design System · Android Edge-to-Edge*)
- **Tech Stack:** Android Kotlin, ViewBinding + Material 3 (tanpa Compose), Manual DI / `SignPdfApplication` singletons (tanpa Hilt).

---

## 1. Keputusan Scope (Final)
1. **Obsidian Dark Mode:** Masuk ke dalam rangkaian revamp (**Phase 6**).
2. **Saved Signatures:** Tetap **2 slot (`TTD` + `PARAF`)** agar 100% kompatibel dengan `signPDF-Backend` Cloud Signature Sync.
3. **Alur Export:** Setelah tap **`Export`**, langsung tampilkan **Export Bottom Sheet** (`Preview PDF` + `Share PDF`). Fitur baru `Save to Device` masuk **TICKET-1** (terpisah).
4. **Floating Dock:** Fokus pada **`Undo`, `Redo`, `Sign`, `Initial`**. Fitur baru `Date Stamp` dan `Highlight Markup` masuk **TICKET-2** dan **TICKET-3** (terpisah).
5. **Workflow Commit:** Setiap selesai 1 phase langsung di-commit ke branch `release/v1.1.0` (tidak perlu build lokal jika tidak ada Android SDK; GitHub Actions `.github/workflows/build-android.yml` otomatis berjalan di `release/**`).

---

## 2. Design System Tokens

### Light Mode — "Ink & Paper" (Sudah diimplementasi di `res/values/colors.xml`)
| Token | Hex | Kegunaan |
|---|---|---|
| `sp_background` (Canvas) | `#F8FAFC` | Latar utama halaman |
| `sp_surface` | `#FFFFFF` | Card, toolbar, bottom sheet |
| `sp_surface_variant` | `#F1F5F9` | Surface 2, badge PDF, account pill |
| `sp_outline` / `sp_outline_variant` | `#E2E8F0` / `#CBD5E1` | Hairline border 1dp / dashed border |
| `sp_primary` (Primary Ink) | `#0F172A` | Teks utama, primary button bg (`#FFFFFF` text) |
| `sp_text_secondary` | `#64748B` | Subtitle, metadata |
| `sp_accent` | `#2563EB` | Sign CTA, active links, icon accent |
| `sp_success` / `sp_success_text` | `#10B981` / `#059669` | Privacy pill, Signed badge (`#1A10B981` tint) |
| `sp_warning` / `sp_warning_text` | `#F59E0B` / `#B45309` | Draft badge (`#1AF59E0B` tint) |
| `sp_pdf_backdrop` | `#E2E8F0` | Latar viewport PDF editor & preview |
| `sp_dock_background` / `sp_dock_border` | `#F00F172A` / `#1FFFFFFF` | Glassmorphic Floating Bottom Dock |
| Ink Colors | `#0F172A`, `#1D4ED8`, `#B91C1C` | Black Ink, Navy Blue Ink, Crimson Ink |

### Dark Mode — "Obsidian" (Untuk Phase 6: `res/values-night/colors.xml`)
| Token | Hex | Catatan |
|---|---|---|
| `sp_background` (Canvas) | `#090D16` | Latar gelap Obsidian |
| `sp_surface` | `#111827` | Card, toolbar, bottom sheet |
| `sp_surface_variant` | `#1E293B` | Surface 2, badge PDF, account pill |
| `sp_outline` / `sp_outline_variant` | `#1E293B` / `#334155` | Border 1dp / dashed border |
| `sp_primary` / `sp_on_primary` | `#F8FAFC` / `#090D16` | Primary button bg terang dengan teks gelap |
| `sp_text_primary` / `sp_text_secondary` | `#F8FAFC` / `#94A3B8` | Teks utama & sekunder |
| `sp_accent` | `#3B82F6` | Accent Blue terang |
| `sp_pdf_backdrop` | `#020617` | Backdrop gelap di belakang halaman PDF putih |
| `sp_dock_background` / `sp_dock_border` | `#F21E293B` / `#2E94A3B8` | Floating Dock Obsidian |

### Corner Radius & Komponen
- **Cards & Dropzone:** `16dp` (`sp_corner_radius_medium`) · `1dp` hairline border
- **Primary Buttons:** `12dp` (`sp_corner_radius_small`) · `48dp` min touch height
- **Bottom Sheet:** `24dp` top radius (`sp_corner_radius_sheet`) · `40×4dp` drag handle
- **Floating Dock & Pills:** `999dp` (`sp_corner_radius_pill`)

---

## 3. Progress & Checklist Per Phase

### [x] Phase 1 — Foundation: "Ink & Paper" Design System & Edge-to-Edge (`f5c40c8`)
- Bump `versionCode = 2`, `versionName = "1.1.0"` di `app/build.gradle.kts`.
- Update `colors.xml`, `dimens.xml`, `themes.xml` (`TextAppearance.SignPDF.*`, `Widget.SignPDF.*`, `ThemeOverlay.SignPDF.BottomSheetDialog`).
- Aktifkan Edge-to-Edge di `MainActivity.kt` + helper `ui/common/InsetsExt.kt` diterapkan ke semua Fragment.
- Restyle `fragment_login.xml` dan `bottomsheet_signature_picker.xml`.

### [x] Phase 2 — Home Screen Revamp (`8ef1648`)
- Top bar dengan brand icon `32×32dp` + Compact Account Pill (`Guest · Log in` / `☁ username` dengan dialog Sync & Log out).
- Hero Dropzone Card (dashed) + Privacy Pill hijau.
- Saved Signatures Strip (2 slot: `TTD` & `PARAF` + `+ New` / `+ Manage`).
- Recent Documents List: `RecentDocument.kt`, `RecentDocumentsStore.kt` (SharedPreferences + JSON, maks. 10 item), `RecentDocumentAdapter.kt`, `item_recent_document.xml`, persistable URI permission, auto-mark `SIGNED` saat export, dan unit test `RecentDocumentsStoreTest.kt`.

### [x] Phase 3 — PDF Editor Revamp (`8b618c9`)
- Top Toolbar bersih: Back, judul + subtitle dinamis `Page X of Y · 100%` (scroll listener + `ZoomableContainer.onScaleChanged`), dan pill CTA **`Export`** di kanan atas.
- Viewport PDF full-height berlatar `@color/sp_pdf_backdrop` (`#E2E8F0`); struktur 4-sisi `ConstraintLayout` di `item_pdf_page.xml` dipertahankan utuh (`fixing-signing.md`).
- Glassmorphic Floating Bottom Dock (`Undo`, `Redo`, divider, pill biru **`Sign`**, dan **`Initial`**) dengan `canUndo`/`canRedo` `StateFlow` di `PdfEditorViewModel`.

### [x] Phase 4 — 1-Tap Sign Bottom Sheet & Inline Draw Canvas (`d3ec468`)
- `SignaturePickerBottomSheet` 2-mode:
  - Mode A: `USE SAVED` (`1-tap place`) horizontal preview (`TTD` & `PARAF`) + 2-column grid (`Draw on Screen`, `Import Image`).
  - Mode B: Inline Draw Canvas dengan 3 ink color dots (`#0F172A`, `#1D4ED8`, `#B91C1C`), placeholder, dashed baseline, `behavior.isDraggable = false` saat menggambar, dan `SignatureViewModel.saveBitmapNow(...)`.
- Restyle `SignatureCanvasFragment` dengan 3 ink color dots untuk akses `+ Manage` dari Home.

---

### [x] Phase 5 — Export ("Document Ready") Bottom Sheet & Restyle Preview *(Selesai)*
- `ic_file_check.xml`, `ic_preview_eye.xml`, `bg_icon_badge_on_accent.xml`, `bottomsheet_export.xml`, dan `ExportBottomSheet.kt`: drag handle `40×4dp`, header badge emerald (`Document Ready`, `<fileName> · <formattedSize>`, tombol close), dan 2-column action grid (`Preview PDF` outlined card + `Share PDF` primary blue `#2563EB`).
- `PdfEditorFragment.kt`: `ExportState.Success` menampilkan `ExportBottomSheet` (dengan deduplikasi tag) dan mendengarkan `ExportBottomSheet.RESULT_KEY` untuk aksi `"preview"` → navigate ke `PdfPreviewFragment`.
- Restyle `fragment_pdf_preview.xml` & `PdfPreviewFragment.kt` dengan toolbar Ink & Paper (back button, judul + filename, badge `Signed`, hairline divider `@color/sp_outline`), viewport `@color/sp_pdf_backdrop`, dan bottom action bar (`Back to Editor` outlined + `Share PDF` accent blue).

---

### [x] Phase 6 — Obsidian Dark Mode & Update Dokumentasi *(Selesai)*
- `app/src/main/res/values-night/colors.xml`: seluruh token **Obsidian Dark** (`sp_background` `#090D16`, `sp_surface` `#111827`, `sp_surface_variant` `#1E293B`, `sp_outline` `#1E293B`, `sp_outline_variant` `#334155`, `sp_primary` `#F8FAFC`, `sp_on_primary` `#090D16`, `sp_text_primary` `#F8FAFC`, `sp_text_secondary` `#94A3B8`, `sp_accent` `#3B82F6`, `sp_pdf_backdrop` `#020617`, `sp_dock_background` `#F21E293B`, `sp_dock_border` `#2E94A3B8`, plus status/container tints yang kontras di latar gelap).
- `app/src/main/res/values/themes.xml`: parent `Theme.SignPDF` diubah ke `Theme.Material3.DayNight.NoActionBar` dan `android:windowLightStatusBar` hardcoded dihapus (sudah diatur dinamis di `MainActivity.configureEdgeToEdge()` berdasarkan `UI_MODE_NIGHT_MASK`).
- `ThemePreferenceStore.kt` (+ unit test `ThemePreferenceStoreTest.kt`): preferensi System / Light / Dark di `SharedPreferences`, diterapkan di `SignPdfApplication.onCreate()` via `AppCompatDelegate.setDefaultNightMode(...)`, dengan toggle ikon Sun/Moon (`ic_sun.xml`, `ic_moon.xml`) di top bar `HomeFragment`.
- **Proteksi kertas putih di Dark Mode:** `img_pdf_page`, kartu preview tanda tangan (`card_saved_ttd`, `card_saved_paraf`, `card_sheet_saved_ttd`, `card_sheet_saved_paraf`), `card_signature_pad`, dan `SignaturePad` (`signature_pad_inline` & `signature_pad`) dikunci berlatar `@android:color/white`; ikon brand di `fragment_home.xml` diberi `app:tint="@color/sp_on_primary"`.
- Update dokumentasi: `CLAUDE.md`, `.github/copilot-instructions.md`, dan `docs/ui-revamp-plan.md`.

---

## 4. Backlog / Tiket Terpisah (Fitur Baru di Luar Revamp UI Inti)

| Tiket | Nama Fitur | Ringkasan Scope |
|---|---|---|
| **TICKET-1** | **Save to Device (`Documents/SignPDF`)** | Tambahkan aksi `Save to Device` di `ExportBottomSheet` menggunakan `MediaStore` (API 29+) dan fallback SAF `CreateDocument` (API 26–28) tanpa `WRITE_EXTERNAL_STORAGE`, plus Snackbar konfirmasi `"Saved to Documents/SignPDF"`. |
| **TICKET-2** | **Date / Initials Text Stamp** | Tambahkan tombol Stamp di Floating Dock, dialog/preset teks (`AM · 20 Nov 2026`), generator bitmap teks transparan atau `OverlayType` baru, serta dukungan drag/resize & embed di `EmbedSignatureToPdfUseCase`. |
| **TICKET-3** | **Highlight Markup Tool** | Tambahkan tombol Highlight di Floating Dock, mode penempatan highlight transparan per halaman di `SignatureOverlayView`, serta rendering kotak highlight di `EmbedSignatureToPdfUseCase`. |
