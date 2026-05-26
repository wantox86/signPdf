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
            updateOverlay(
                selected.copy(
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
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val selected = selectedOverlay()
                if (selected != null) {
                    val selectedRect = overlayRect(selected)
                    if (closeRect(selectedRect).contains(event.x, event.y)) {
                        overlays.removeAll { it.id == selected.id }
                        selectedOverlayId = null
                        onOverlaysChanged(overlays.toList())
                        invalidate()
                        return true
                    }
                }

                val touched = overlays.asReversed().firstOrNull { overlayRect(it).contains(event.x, event.y) }
                selectedOverlayId = touched?.id
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                return touched != null
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) {
                    return true
                }

                val selected = selectedOverlay() ?: return false
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y

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
