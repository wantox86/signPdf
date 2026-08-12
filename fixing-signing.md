# fixing-signing.md — Analisa mendalam: TTD/Initial tidak muncul di hasil export & hilang-muncul saat scroll

> Dokumen handoff buat sesi/model berikutnya yang bakal ngerjain fixing-nya.
> Ditulis 2026-08-12 setelah beberapa ronde fix yang MENGURANGI gejala tapi belum nyelesaiin akar masalah.
> Baca bagian "Root cause" dulu — itu kesimpulan utamanya. Jangan ulangi fix-fix yang udah terbukti bukan akar masalah (ada daftarnya di bawah).

---

## 1. Gejala yang dilaporkan user (hasil testing di device fisik, APK debug via CI)

1. **TTD/Initial nggak muncul di layar Preview** (hasil export), padahal diagnostic bilang "Overlays to embed: 1".
2. Balik dari Preview ke Editor → **TTD-nya muncul lagi, posisinya "bener" secara visual di editor**.
3. Saat scroll di editor, **TTD kadang hilang di titik scroll tertentu, muncul lagi kalau di-scroll balik**.
4. Preview selalu mulai dari halaman paling atas (padahal TTD-nya di halaman bawah) — minor UX, tapi memperparah kesan "TTD hilang".

## 2. Data diagnostic kunci (dari dialog "Export diagnostics" di device)

```
Overlays to embed: 1
Page 3: mediaBox=841.92x595.32pt rotation=0 displayWH=841.92x595.32pt
  actualRenderedWidthPx=1080.0 scaleX=0.77955556 scaleY=0.77955556
  overlay e6d7fd21: bitmap=1124x2206 config=ARGB_8888 hasAlpha=true isRecycled=false
    PDImageXObject created: 1124x2206 isStencil=false
    placed at pdfX=342.8796 pdfY=-270.747 w=76.65219 h=150.44014 (page bounds 0..841.92 x 0..595.32)
    fully in-bounds: false
```

Fakta penting dari data ini:
- Dokumen test: **landscape** 841.92 x 595.32 pt (A4 landscape), 6 halaman, TANPA rotasi (`/Rotate=0`), nggak encrypted. File: RFC form kantor.
- Bitmap TTD sehat: ARGB_8888, hasAlpha, nggak recycled. `PDImageXObject` kebentuk normal. **Pipeline embed PDFBox-android SENDIRI nggak bermasalah** — sudah diverifikasi terpisah (lihat §4).
- Masalahnya murni **koordinat**: `pdfY = -270.747` → jauh di bawah halaman.

### Hitung balik (matematika)

Tinggi halaman ter-render: `1080 × (595.32 / 841.92) ≈ 763.7 px`.

Dari rumus `pdfY = displayHeight − overlay.y×scaleY − overlay.height×scaleY`:

```
-270.747 = 595.32 − overlay.y×0.77956 − 150.44
overlay.y ≈ 918 px
```

`overlay.y ≈ 918px` padahal **tinggi halaman cuma ≈ 764px** → koordinat yang TERSIMPAN di state itu ~154px melewati batas bawah halaman 3. Overlay height ≈ 193px, jadi seluruh overlay ada "di bawah" halaman 3 dalam ruang page-local → pas di-embed, jatuh di luar kertas → invisible di export/preview. **Angka 918 ≈ posisi layar yang secara visual jatuh di halaman BERIKUTNYA (halaman 4) atau di gap antar halaman.**

## 3. ROOT CAUSE (kesimpulan utama)

**Cacat arsitektur pada mapping koordinat overlay, bukan bug di embed/PDFBox.**

Arsitektur sekarang di `PdfEditorFragment` + `SignatureOverlayView`:

- `SignatureOverlayView` itu **SATU view fullscreen** yang ditumpuk di atas RecyclerView (sibling dalam FrameLayout, `match_parent`), BUKAN bagian dari tiap item halaman.
- Konsep "halaman aktif" = `currentPageIndex()` = `LinearLayoutManager.findFirstVisibleItemPosition()` — yaitu **item pertama yang kelihatan**, walau cuma kelihatan 1px di ujung atas layar.
- Semua koordinat overlay disimpan "page-local" relatif ke halaman aktif itu, dikonversi bolak-balik pakai `setPageOffset(pageView.left, pageView.top)`.
- Overlay HANYA digambar untuk halaman aktif: `allOverlays.filter { it.pageIndex == currentPageIndex() }`.

Konsekuensi fatal (masing-masing menjelaskan satu gejala):

1. **User bisa drag TTD ke mana aja di layar** — termasuk area yang secara visual ada di halaman BERIKUTNYA (saat 2 halaman kelihatan sebagian, kondisi paling normal pas scroll). Koordinatnya tetap disimpan relatif ke halaman pertama-yang-kelihatan → `overlay.y` bisa > tinggi halaman (kasus 918 > 764 di atas). `pageIndex` yang tersimpan pun salah (halaman atas, bukan halaman tempat TTD kelihatan nempel).
2. **Di editor keliatan "bener"** karena digambar pakai mapping yang sama-sama salah (salah yang konsisten) → user nggak sadar ada masalah sampai lihat hasil export. Ini yang bikin "balik ke editor muncul ttd-nya".
3. **TTD hilang-muncul saat scroll**: begitu scroll bikin `findFirstVisibleItemPosition()` berubah (halaman TTD bukan lagi yang pertama kelihatan, meskipun masih di layar), filter `pageIndex == currentPageIndex()` bikin overlay itu NGGAK digambar → hilang. Scroll balik → jadi first-visible lagi → muncul. Persis seperti laporan user.
4. Bonus bug laten: callback `onOverlaysChanged` me-remap SEMUA overlay yang lagi kelihatan ke `currentPageIndex()` (`.map { it.copy(pageIndex = pageIndex) }`) — drag saat posisi scroll beda bisa MINDAHIN pageIndex overlay diam-diam.

## 4. Yang SUDAH diverifikasi / fix yang SUDAH dicoba (jangan diulang)

| Hipotesis / fix | Status | Bukti |
|---|---|---|
| PDF rusak / encrypted / CropBox aneh | ❌ Bukan | Diinspeksi langsung pakai pypdf: normal semua |
| Page rotation (`/Rotate`) | ❌ Bukan (untuk dokumen ini) | `Rotate=0` semua halaman. Kode kompensasi rotasi udah ditambahin & valid, biarin aja |
| Matematika `PdfCoordinateConverter` salah | ❌ Bukan | Direproduksi via reportlab+pypdf dengan koordinat identik → kotak muncul PAS. Ada unit test-nya juga |
| PDFBox-android drawImage/LosslessFactory buggy | ❌ Bukan | API signature diverifikasi dari AAR asli; PDImageXObject kebentuk normal (lihat diagnostic) |
| Bitmap HARDWARE config / recycled | ❌ Bukan | Diagnostic: ARGB_8888, hasAlpha=true, isRecycled=false |
| Race: addOverlay sebelum halaman ke-render (fallback ukuran potrait hardcoded) | ✅ Bug nyata, SUDAH di-fix (FAB disabled saat loading + addOverlay no-op kalau bitmap null) | Tapi TERBUKTI BUKAN satu-satunya — gejala masih terjadi setelah fix ini |
| Scroll offset nggak dikompensasi di SignatureOverlayView | ✅ Sebagian di-fix (`setPageOffset`) | Memperbaiki drag saat scroll di halaman aktif, tapi nggak menyelesaikan masalah lintas-halaman (§3) |

Diagnostic dialog (copyable) di `PdfEditorFragment.showExportDiagnostics()` + logging di `EmbedSignatureToPdfUseCase` masih terpasang — **pertahankan sampai fix beres & terverifikasi**, baru dicabut.

## 5. Rekomendasi perbaikan

### Opsi A (RECOMMENDED, benar secara struktural): pindahin overlay ke DALAM item RecyclerView

Jadikan overlay bagian dari tiap item halaman (`item_pdf_page.xml`), bukan satu view global:

1. Tambahin `SignatureOverlayView` (atau versi per-halaman yang disederhanakan) ke dalam `item_pdf_page.xml`, nempel persis di atas `img_pdf_page` (FrameLayout, ukuran match si ImageView).
2. `PdfPageAdapter` menerima `Map<Int, List<SignatureOverlay>>` (overlay per pageIndex) + callback perubahan; `onBindViewHolder` set overlay milik halaman itu doang ke overlay view item tsb.
3. Koordinat overlay otomatis page-local MURNI (view-nya sendiri emang seukuran halaman) → nggak ada lagi konsep pageOffset, nggak ada lagi `currentPageIndex()` buat koordinat. Hapus `setPageOffset` & filter first-visible.
4. `pageIndex` overlay = posisi adapter item tempat user nyentuh → nggak mungkin ketuker lagi.
5. Drag ke luar batas item → clamp ke bounds halaman (jangan biarin koordinat keluar 0..width/height).
6. Scroll jadi otomatis bener: overlay ikut halamannya karena emang satu view hierarchy → gejala hilang-muncul lenyap by construction.
7. Perhatian teknis: touch conflict antara drag overlay vs scroll RecyclerView — pakai `requestDisallowInterceptTouchEvent(true)` saat overlay kena ACTION_DOWN. Ini penting, jangan kelewat.
8. Satu bitmap halaman punya ukuran sama dengan yang dipakai embed (RENDER_WIDTH_PX lebar) — scaling ImageView (fitCenter, adjustViewBounds) bikin ukuran VIEW ≠ ukuran BITMAP. Simpan koordinat overlay dalam ruang bitmap (kalikan `bitmap.width / view.width`), atau set view supaya 1:1. **Ini titik paling rawan bug baru — hitung dengan sadar.**

### Opsi B (patch minimal kalau mau cepat, kurang bersih): benerin mapping di arsitektur sekarang

1. Saat ACTION_DOWN/penempatan overlay: tentuin halaman target dengan **hit-test terhadap SEMUA item yang kelihatan** (`layoutManager.findFirstVisibleItemPosition()..findLastVisibleItemPosition()`, cek `findViewByPosition(i)` bounds mana yang memuat titik sentuh), bukan asal first-visible.
2. Simpan & konversi koordinat pakai offset halaman TARGET itu (bukan halaman first-visible).
3. Gambar overlay untuk SEMUA halaman yang kelihatan (loop visible range, translate canvas per halaman), bukan cuma first-visible → fix gejala hilang-muncul.
4. Clamp koordinat overlay ke bounds halamannya saat drag/drop & saat addOverlay.
5. Hapus perilaku `onOverlaysChanged` yang me-remap pageIndex massal (bug laten §3.4).

Opsi B lebih sedikit file yang berubah tapi nyisain kompleksitas mapping manual yang rapuh. Kalau waktunya ada, **kerjakan Opsi A**.

### Perbaikan pelengkap (dua-duanya perlu, apapun opsinya)

- **Preview scroll ke halaman ber-TTD**: `PdfPreviewFragment` terima argumen `firstSignedPage` (min pageIndex dari overlay yang di-embed), lalu `recyclerPreviewPages.scrollToPosition(firstSignedPage)` setelah `Ready`. Menjawab keluhan "preview balik ke atas".
- **Validasi terakhir di embed**: di `EmbedSignatureToPdfUseCase`, kalau hasil hitung `fully in-bounds: false` → **clamp ke dalam halaman** (atau minimal throw/warn keras) — jangan pernah diem-diem menggambar di luar kertas. Ini safety net terakhir, bukan pengganti fix utama.
- Setelah fix terverifikasi di device: cabut dialog diagnostic export (ganti balik ke navigate langsung), tapi pertahankan logging-nya di level use case kalau mau (murah).

## 6. Test plan verifikasi (di device fisik, dokumen RFC landscape 6 halaman yang sama)

1. Buka PDF, tunggu render selesai → tambah Sign di halaman 1 (tanpa scroll) → export → **muncul di preview, posisi sama** ✔
2. Scroll ke halaman 3-4 (posisi scroll "nanggung", dua halaman kelihatan) → tambah Sign yang secara visual ada di halaman BAWAH dari dua yang kelihatan → export → muncul di posisi yang sama ✔ (ini kasus yang selama ini gagal)
3. Drag TTD melewati batas halaman → harus ke-clamp / pindah pageIndex dengan benar, diagnostic `fully in-bounds: true` selalu ✔
4. Scroll naik-turun berkali-kali → TTD nggak pernah hilang/kedip di titik manapun ✔
5. Preview otomatis ke halaman ber-TTD ✔
6. Regression: resize (pinch, anchor center, hit-target margin 40px), delete (tombol x), undo/redo, multi-overlay di halaman berbeda, share ke WA/Telegram ✔

## 7. Konteks teknis singkat proyek (biar nggak perlu re-discover)

- Repo: `~/Documents/Github/signPdf`, branch aktif `release/1.0.0` (push → CI GitHub Actions auto-build APK debug, artifact `signpdf-debug-apk`; keystore debug tetap udah di-commit jadi update install nggak conflict lagi).
- Tanpa Android SDK lokal — verifikasi compile via `./gradlew tasks` lokal (cek config doang) + CI untuk compile beneran. Test di device oleh user (manual), feedback via chat.
- File kunci: `ui/editor/PdfEditorFragment.kt`, `ui/editor/SignatureOverlayView.kt`, `ui/editor/PdfPageAdapter.kt`, `ui/editor/PdfEditorViewModel.kt`, `domain/usecase/EmbedSignatureToPdfUseCase.kt`, `domain/util/PdfCoordinateConverter.kt` (+ unit test-nya), `ui/preview/PdfPreviewFragment.kt`.
- Render halaman: eager semua halaman upfront (progresif per halaman), lebar tetap `RenderPdfPageUseCase.RENDER_WIDTH_PX = 1080`, tinggi proporsional. FAB tambah disabled selama `isLoadingPages`.
- pdfbox-android 2.0.27.0; `PDFBoxResourceLoader.init()` dipanggil di `SignPdfApplication`. Crash handler global + dialog copyable udah ada.
