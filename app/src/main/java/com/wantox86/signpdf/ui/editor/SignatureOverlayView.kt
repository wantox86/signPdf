package com.wantox86.signpdf.ui.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.wantox86.signpdf.domain.model.SignatureOverlay
import kotlin.math.max

/**
 * Overlay TTD/paraf buat SATU halaman -- sekarang jadi anak langsung dari item_pdf_page.xml
 * (bukan lagi satu view fullscreen yang numpuk di atas seluruh RecyclerView). Konsekuensinya:
 * overlay.x/y/width/height (ruang koordinat bitmap halaman, sama kayak yang dipakai
 * EmbedSignatureToPdfUseCase buat embed) otomatis "milik" halaman ini doang -- nggak ada lagi
 * urusan scroll-offset/halaman-mana-yang-first-visible kayak desain lama yang jadi sumber bug
 * overlay salah tempat/ilang-muncul pas scroll (lihat fixing-signing.md).
 */
class SignatureOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onOverlaysChanged: (List<SignatureOverlay>) -> Unit = {}

    private val overlays = mutableListOf<SignatureOverlay>()
    private var selectedOverlayId: String? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Perubahan drag/resize di-commit ke luar (onOverlaysChanged) SEKALI pas gesture selesai
    // (ACTION_UP/CANCEL), bukan per event move -- commit per-move bikin Fragment/adapter
    // notifyItemChanged() ke item yang lagi disentuh, RecyclerView rebind view-nya di tengah
    // gesture, dan touch stream-nya putus (gejala "TTD nggak bisa digeser"). Bonus: undo/redo
    // jadi per gesture utuh, bukan per piksel gerakan.
    private var hasPendingCommit = false

    // Ukuran bitmap halaman yang lagi dibind -- overlay.x/y/width/height selalu dalam ruang
    // koordinat INI (bitmap-pixel-space), sementara View-nya sendiri dirender di ukuran layar
    // (dp*density, biasanya beda dari ukuran bitmap asli karena ImageView fitCenter). scale()
    // di bawah yang jembatanin dua ruang koordinat itu.
    private var bitmapWidth = 0f
    private var bitmapHeight = 0f

    fun setPageBitmapSize(width: Int, height: Int) {
        bitmapWidth = width.toFloat()
        bitmapHeight = height.toFloat()
        invalidate()
    }

    // View (fitCenter, adjustViewBounds) ngikutin aspect ratio bitmap, jadi 1 scale factor
    // berlaku sama buat X & Y.
    private fun scale(): Float {
        if (bitmapWidth <= 0f || width <= 0) return 1f
        return width.toFloat() / bitmapWidth
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#33B5E5")
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33B5E5")
    }

    private val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
    }

    private val closeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val selected = selectedOverlay() ?: return false
            val factor = detector.scaleFactor
            // Batas minimum resize -- sebelumnya 80x40px kegedean buat page yang di-render
            // ~1080px lebar, nggak bisa diperkecil sampe wajar buat paraf kecil.
            val newWidth = max(30f, selected.width * factor)
            val newHeight = max(15f, selected.height * factor)
            // Anchor resize ke center overlay, bukan ke pojok kiri-atas -- sebelumnya x/y
            // dibiarin tetap pas width/height berubah, jadi box-nya "kabur" ngembang ke
            // kanan-bawah tiap discale alih-alih membesar/mengecil di tempat.
            val centerX = selected.x + selected.width / 2f
            val centerY = selected.y + selected.height / 2f
            updateOverlay(
                selected.copy(
                    x = centerX - newWidth / 2f,
                    y = centerY - newHeight / 2f,
                    width = newWidth,
                    height = newHeight
                )
            )
            return true
        }
    })

    fun setOverlays(list: List<SignatureOverlay>) {
        overlays.clear()
        overlays.addAll(list)
        if (selectedOverlayId != null && overlays.none { it.id == selectedOverlayId }) {
            selectedOverlayId = null
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val s = scale()
        canvas.save()
        canvas.scale(s, s)
        overlays.forEach { overlay ->
            val dst = RectF(
                overlay.x,
                overlay.y,
                overlay.x + overlay.width,
                overlay.y + overlay.height
            )
            canvas.drawBitmap(overlay.bitmap, null, dst, null)

            if (overlay.id == selectedOverlayId) {
                canvas.drawRect(dst, borderPaint)
                canvas.drawCircle(dst.right, dst.bottom, 14f, handlePaint)

                val closeRect = closeRect(dst)
                canvas.drawOval(closeRect, closePaint)
                canvas.drawText(
                    "x",
                    closeRect.centerX(),
                    closeRect.centerY() + 9f,
                    closeTextPaint
                )
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        // Semua hit-test/drag di bawah ini kerja di ruang koordinat bitmap (sama kayak
        // overlay.x/y yang tersimpan), jadi konversi dulu dari koordinat layar (event.x/y).
        val s = scale()
        val touchX = if (s != 0f) event.x / s else event.x
        val touchY = if (s != 0f) event.y / s else event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val selected = selectedOverlay()
                if (selected != null) {
                    val selectedRect = overlayRect(selected)
                    if (closeRect(selectedRect).contains(touchX, touchY)) {
                        overlays.removeAll { it.id == selected.id }
                        selectedOverlayId = null
                        onOverlaysChanged(overlays.toList())
                        invalidate()
                        return true
                    }
                }

                // Pake hitTestRect (bukan overlayRect polos) buat nangkep touch pertama --
                // overlay/paraf kecil punya area gambar yang sempit, kalau hit-test-nya persis
                // sama batas gambar, jari pertama pinch-to-resize gampang banget meleset dan
                // gesture-nya nggak ke-capture sama sekali (makanya kerasa kayak "resize nggak
                // jalan").
                val touched = overlays.asReversed().firstOrNull { hitTestRect(it).contains(touchX, touchY) }
                selectedOverlayId = touched?.id
                lastTouchX = touchX
                lastTouchY = touchY
                invalidate()
                // Overlay ini sekarang hidup di dalam item RecyclerView -- kalau nangkep drag,
                // cegah parent (RecyclerView) ikut interpretasi gesture yang sama sebagai
                // scroll, biar nggak "tarik-tarikan" antara drag overlay vs scroll dokumen.
                if (touched != null) parent?.requestDisallowInterceptTouchEvent(true)
                return touched != null
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) {
                    return true
                }

                val selected = selectedOverlay() ?: return false
                val dx = touchX - lastTouchX
                val dy = touchY - lastTouchY
                lastTouchX = touchX
                lastTouchY = touchY

                updateOverlay(
                    selected.copy(
                        x = selected.x + dx,
                        y = selected.y + dy
                    )
                )
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (hasPendingCommit) {
                    hasPendingCommit = false
                    onOverlaysChanged(overlays.toList())
                }
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun selectedOverlay(): SignatureOverlay? {
        val id = selectedOverlayId ?: return null
        return overlays.firstOrNull { it.id == id }
    }

    private fun updateOverlay(overlay: SignatureOverlay) {
        val idx = overlays.indexOfFirst { it.id == overlay.id }
        if (idx >= 0) {
            val clamped = clampToPage(overlay)
            overlays[idx] = clamped
            // Update visual lokal doang -- commit ke luar ditunda sampai gesture selesai
            // (lihat komentar hasPendingCommit).
            hasPendingCommit = true
            invalidate()
        }
    }

    // Safety net: overlay nggak boleh digambar/tersimpan di luar batas bitmap halaman -- kalau
    // dibiarin lolos, ujung-ujungnya ke-embed di luar kertas pas export (invisible, lihat
    // fixing-signing.md). Clamp posisi & ukuran di titik tunggal ini (dipanggil dari drag &
    // resize) biar nggak ada celah lain.
    private fun clampToPage(overlay: SignatureOverlay): SignatureOverlay {
        if (bitmapWidth <= 0f || bitmapHeight <= 0f) return overlay
        val clampedWidth = overlay.width.coerceIn(1f, bitmapWidth)
        val clampedHeight = overlay.height.coerceIn(1f, bitmapHeight)
        val clampedX = overlay.x.coerceIn(0f, (bitmapWidth - clampedWidth).coerceAtLeast(0f))
        val clampedY = overlay.y.coerceIn(0f, (bitmapHeight - clampedHeight).coerceAtLeast(0f))
        return overlay.copy(x = clampedX, y = clampedY, width = clampedWidth, height = clampedHeight)
    }

    private fun overlayRect(overlay: SignatureOverlay): RectF {
        return RectF(
            overlay.x,
            overlay.y,
            overlay.x + overlay.width,
            overlay.y + overlay.height
        )
    }

    // Area khusus buat nangkep sentuhan awal (ACTION_DOWN) -- lebih gede dari area gambar
    // sebenarnya, biar overlay kecil (paraf/initial) tetep gampang di-tap/pinch jarinya.
    // Margin dalam ruang bitmap, dibagi scale() biar tetap kerasa konsisten di layar berapa
    // pun ukuran render-nya.
    private fun hitTestRect(overlay: SignatureOverlay): RectF {
        val s = scale()
        val margin = if (s != 0f) 40f / s else 40f
        val rect = overlayRect(overlay)
        return RectF(
            rect.left - margin,
            rect.top - margin,
            rect.right + margin,
            rect.bottom + margin
        )
    }

    private fun closeRect(overlayRect: RectF): RectF {
        val size = 34f
        return RectF(
            overlayRect.right - size / 2f,
            overlayRect.top - size / 2f,
            overlayRect.right + size / 2f,
            overlayRect.top + size / 2f
        )
    }
}
