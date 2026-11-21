package com.wantox86.signpdf.ui.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.wantox86.signpdf.MainDispatcherRule
import com.wantox86.signpdf.domain.model.OverlayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PdfEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: PdfEditorViewModel
    private lateinit var pageBitmap: Bitmap
    private lateinit var signatureBitmap: Bitmap

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = PdfEditorViewModel(app)
        pageBitmap = Bitmap.createBitmap(1000, 1400, Bitmap.Config.ARGB_8888)
        signatureBitmap = Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888)
        viewModel.setPagesForTesting(listOf(pageBitmap, pageBitmap))
    }

    @Test
    fun `adding one signature then undoing once removes it and flips undo-redo availability`() {
        assertFalse(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)

        viewModel.addOverlay(OverlayType.TTD, signatureBitmap, pageIndex = 0)
        assertEquals(1, viewModel.overlays.value.size)
        assertTrue(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)

        viewModel.undoOverlay()
        assertTrue(viewModel.overlays.value.isEmpty())
        assertFalse(viewModel.canUndo.value)
        assertTrue(viewModel.canRedo.value)
    }

    @Test
    fun `redoing once after undo restores signature and flips undo-redo availability`() {
        viewModel.addOverlay(OverlayType.TTD, signatureBitmap, pageIndex = 0)
        val placed = viewModel.overlays.value.single()

        viewModel.undoOverlay()
        assertTrue(viewModel.overlays.value.isEmpty())

        viewModel.redoOverlay()
        assertEquals(listOf(placed), viewModel.overlays.value)
        assertTrue(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)
    }

    @Test
    fun `updateOverlaysForPage with unchanged overlays does not push extra undo snapshot`() {
        viewModel.addOverlay(OverlayType.TTD, signatureBitmap, pageIndex = 0)
        viewModel.addOverlay(OverlayType.PARAF, signatureBitmap, pageIndex = 1)
        val page0Overlays = viewModel.overlays.value.filter { it.pageIndex == 0 }

        // Even though page 0 is before page 1 in the list, reporting identical overlays for page 0
        // must not re-order and push a spurious history snapshot.
        viewModel.updateOverlaysForPage(0, page0Overlays)

        viewModel.undoOverlay()
        assertEquals(1, viewModel.overlays.value.size)
        assertEquals(0, viewModel.overlays.value.single().pageIndex)
        assertTrue(viewModel.canUndo.value)

        viewModel.undoOverlay()
        assertTrue(viewModel.overlays.value.isEmpty())
        assertFalse(viewModel.canUndo.value)
        assertTrue(viewModel.canRedo.value)
    }

    @Test
    fun `moving an overlay pushes one snapshot so undo restores position then removes overlay`() {
        viewModel.addOverlay(OverlayType.TTD, signatureBitmap, pageIndex = 0)
        val initial = viewModel.overlays.value.single()
        val moved = initial.copy(x = initial.x + 50f, y = initial.y + 40f)

        viewModel.updateOverlaysForPage(0, listOf(moved))
        assertEquals(moved, viewModel.overlays.value.single())

        viewModel.undoOverlay()
        assertEquals(initial, viewModel.overlays.value.single())
        assertTrue(viewModel.canUndo.value)
        assertTrue(viewModel.canRedo.value)

        viewModel.undoOverlay()
        assertTrue(viewModel.overlays.value.isEmpty())
        assertFalse(viewModel.canUndo.value)
        assertTrue(viewModel.canRedo.value)
    }
}
