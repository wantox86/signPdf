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

class SignatureOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onOverlaysChanged: (List<SignatureOverlay>) -> Unit = {}

    private val overlays = mutableListOf<SignatureOverlay>()
    private var selectedOverlayId: String? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // overlay.x/y itu koordinat page-local (relatif ke pojok kiri-atas bitmap halaman, dipake
    // juga sama EmbedSignatureToPdfUseCase pas nge-embed ke PDF asli). View ini sendiri adalah
    // sibling di atas RecyclerView yang nggak ikut discroll -- tanpa offset ini, begitu halaman
    // discroll posisi gambar/hit-test overlay nggak nyambung lagi sama posisi asli di halaman,
    // dan kalau overlay di-drag/di-resize pas lagi discroll, koordinat yang kesimpen ikut korup
    // (numpang ke-mix sama scroll offset), yang ujungnya bikin overlay ke-embed di posisi salah
    // (di luar halaman) pas export.
    private var pageOffsetX = 0f
    private var pageOffsetY = 0f

    fun setPageOffset(offsetX: Float, offsetY: Float) {
        if (pageOffsetX == offsetX && pageOffsetY == offsetY) return
        pageOffsetX = offsetX
        pageOffsetY = offsetY
        invalidate()
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
            val newWidth = max(80f, selected.width * factor)
            val newHeight = max(40f, selected.height * factor)
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

        canvas.save()
        canvas.translate(pageOffsetX, pageOffsetY)
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

        // Semua hit-test/drag di bawah ini kerja di ruang koordinat page-local (sama kayak
        // overlay.x/y yang tersimpan), jadi konversi dulu dari koordinat layar (event.x/y)
        // sebelum dipakai -- lihat komentar pageOffsetX/Y di atas.
        val touchX = event.x - pageOffsetX
        val touchY = event.y - pageOffsetY

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

                val touched = overlays.asReversed().firstOrNull { overlayRect(it).contains(touchX, touchY) }
                selectedOverlayId = touched?.id
                lastTouchX = touchX
                lastTouchY = touchY
                invalidate()
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
            MotionEvent.ACTION_CANCEL -> return true
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
            overlays[idx] = overlay
            onOverlaysChanged(overlays.toList())
            invalidate()
        }
    }

    private fun overlayRect(overlay: SignatureOverlay): RectF {
        return RectF(
            overlay.x,
            overlay.y,
            overlay.x + overlay.width,
            overlay.y + overlay.height
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
