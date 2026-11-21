package com.wantox86.signpdf.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.SignatureOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SignatureOverlayViewTest {
    private lateinit var view: SignatureOverlayView
    private lateinit var overlay: SignatureOverlay

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        view = SignatureOverlayView(context)
        view.layout(0, 0, 1000, 1400)
        view.setPageBitmapSize(1000, 1400)
        overlay = SignatureOverlay(
            type = OverlayType.TTD,
            bitmap = Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888),
            pageIndex = 0,
            x = 400f,
            y = 600f,
            width = 100f,
            height = 40f
        )
        view.setOverlays(listOf(overlay))
    }

    @Test
    fun `tap with micro-jitter below touchSlop does not emit onOverlaysChanged`() {
        var commitCount = 0
        view.onOverlaysChanged = { commitCount++ }

        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 450f, 620f, 0)
        val microMove = MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_MOVE, 451f, 621f, 0)
        val up = MotionEvent.obtain(0L, 32L, MotionEvent.ACTION_UP, 451f, 621f, 0)

        view.onTouchEvent(down)
        view.onTouchEvent(microMove)
        view.onTouchEvent(up)

        down.recycle()
        microMove.recycle()
        up.recycle()

        assertEquals(0, commitCount)
    }

    @Test
    fun `drag beyond touchSlop emits onOverlaysChanged once on ACTION_UP`() {
        val commits = mutableListOf<List<SignatureOverlay>>()
        view.onOverlaysChanged = { commits.add(it) }

        val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
        val delta = slop + 40f

        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 450f, 620f, 0)
        val move = MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_MOVE, 450f + delta, 620f + delta, 0)
        val up = MotionEvent.obtain(0L, 32L, MotionEvent.ACTION_UP, 450f + delta, 620f + delta, 0)

        view.onTouchEvent(down)
        view.onTouchEvent(move)
        view.onTouchEvent(up)

        down.recycle()
        move.recycle()
        up.recycle()

        assertEquals(1, commits.size)
        val moved = commits.single().single()
        assertTrue(moved.x > overlay.x)
        assertTrue(moved.y > overlay.y)
    }
}
