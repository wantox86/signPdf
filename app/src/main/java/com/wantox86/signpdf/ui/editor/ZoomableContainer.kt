package com.wantox86.signpdf.ui.editor

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Bungkus SATU child (RecyclerView halaman PDF) buat pinch-to-zoom + pan -- satu level zoom
 * berlaku ke SEMUA halaman sekaligus (bukan per-halaman), nggak nyentuh SignatureOverlayView
 * atau overlay.x/y/width/height SAMA SEKALI. Zoom murni transform visual (scaleX/scaleY/
 * translationX/translationY di child View); koordinat overlay yang tersimpan di ViewModel tetap
 * dalam ruang bitmap-pixel-space yang sama, nggak pernah ke-mix sama zoom level.
 *
 * Disambiguasi drag-overlay vs pinch-zoom-halaman: kalau ACTION_DOWN kesentuh di overlay yang
 * lagi selected, SignatureOverlayView (lihat file itu) udah manggil
 * requestDisallowInterceptTouchEvent(true) -- ini flag bawaan Android yang bikin
 * onInterceptTouchEvent() nggak pernah dipanggil lagi di SEMUA ancestor (termasuk container ini)
 * buat sisa gesture itu, walau jari kedua nempel buat coba pinch. Jadi drag/resize overlay tetap
 * aman nggak ke-ganggu tanpa perlu kode tambahan di SignatureOverlayView sama sekali.
 *
 * Batasan yang disadari: kalau user udah mulai scroll vertikal (RecyclerView keburu narik
 * requestDisallowInterceptTouchEvent buat scroll-nya sendiri, ini emang standar Android buat
 * nested scrolling view) BARU nambahin jari kedua buat pinch, container ini nggak kebagian
 * kesempatan intercept. Pola pemakaian normal (dua jari nempel bareng buat mulai pinch) nggak
 * kena batasan ini.
 */
class ZoomableContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 3f
    }

    private var scale = MIN_SCALE
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isIntercepting = false
    private var lastX = 0f
    private var lastY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val target = targetChild() ?: return false
                scale = (scale * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                applyTransform(target)
                return true
            }
        }
    )

    private val doubleTapDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val target = targetChild() ?: return false
                scale = MIN_SCALE
                applyTransform(target)
                return true
            }
        }
    )

    private fun targetChild(): View? = if (childCount > 0) getChildAt(0) else null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isIntercepting = false
                lastX = ev.x
                lastY = ev.y
            }

            // Jari kedua nempel = pasti mau pinch (bukan scroll/drag biasa), tangkep langsung.
            MotionEvent.ACTION_POINTER_DOWN -> isIntercepting = true

            MotionEvent.ACTION_MOVE -> {
                if (!isIntercepting && scale > MIN_SCALE && ev.pointerCount == 1) {
                    val dx = ev.x - lastX
                    val dy = ev.y - lastY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        isIntercepting = true
                    }
                }
            }
        }
        return isIntercepting
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(ev)
        doubleTapDetector.onTouchEvent(ev)

        val target = targetChild()
        if (target != null && ev.actionMasked == MotionEvent.ACTION_MOVE &&
            ev.pointerCount == 1 && scale > MIN_SCALE
        ) {
            val dx = ev.x - lastX
            val dy = ev.y - lastY
            target.translationX = clampTranslation(target.translationX + dx, target.width)
            target.translationY = clampTranslation(target.translationY + dy, target.height)
            lastX = ev.x
            lastY = ev.y
        }

        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            isIntercepting = false
        }
        return true
    }

    private fun clampTranslation(value: Float, dimension: Int): Float {
        val maxOffset = dimension * (scale - 1f) / 2f
        return value.coerceIn(-maxOffset, maxOffset)
    }

    private fun applyTransform(target: View) {
        target.scaleX = scale
        target.scaleY = scale
        if (scale <= MIN_SCALE) {
            target.translationX = 0f
            target.translationY = 0f
        } else {
            target.translationX = clampTranslation(target.translationX, target.width)
            target.translationY = clampTranslation(target.translationY, target.height)
        }
    }
}
