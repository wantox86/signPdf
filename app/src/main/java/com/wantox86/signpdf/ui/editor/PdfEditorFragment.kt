package com.wantox86.signpdf.ui.editor

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.wantox86.signpdf.databinding.FragmentPdfEditorBinding
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.SignatureOverlay
import com.wantox86.signpdf.ui.signature.SignaturePickerBottomSheet
import com.wantox86.signpdf.ui.signature.SignatureViewModel
import kotlinx.coroutines.flow.collectLatest

class PdfEditorFragment : Fragment() {
    private var _binding: FragmentPdfEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PdfEditorViewModel by viewModels()
    private val signatureViewModel: SignatureViewModel by activityViewModels()
    private val adapter = PdfPageAdapter()
    private var pendingImportType: OverlayType = OverlayType.TTD
    private var awaitingOverlayType: OverlayType? = null
    private var awaitingPreviousBitmapRef: Any? = null
    private var allOverlays: List<SignatureOverlay> = emptyList()
    private var progressDialog: AlertDialog? = null

    private val importImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { signatureViewModel.importFromUri(it, pendingImportType) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerPdfPages.adapter = adapter

        // Overlay sekarang jadi bagian dari tiap item halaman (lihat PdfPageAdapter /
        // SignatureOverlayView) -- callback ini udah dikasih tau pageIndex halaman yang bener
        // dari adapter, nggak perlu nebak/remap pakai currentPageIndex() lagi kayak desain lama
        // (yang jadi sumber bug overlay ke-taruh di halaman salah pas scroll).
        adapter.onOverlaysChangedForPage = { pageIndex, overlaysForThatPage ->
            val merged = allOverlays.filter { it.pageIndex != pageIndex } + overlaysForThatPage
            viewModel.updateOverlays(merged)
        }

        binding.fabAddTtd.setOnClickListener {
            SignaturePickerBottomSheet.newInstance(
                overlayType = OverlayType.TTD,
                hasSavedSignature = signatureViewModel.ttdBitmap.value != null
            )
                .show(parentFragmentManager, "signature_picker_ttd")
        }

        binding.fabAddParaf.setOnClickListener {
            SignaturePickerBottomSheet.newInstance(
                overlayType = OverlayType.PARAF,
                hasSavedSignature = signatureViewModel.parafBitmap.value != null
            )
                .show(parentFragmentManager, "signature_picker_paraf")
        }

        binding.btnSaveAndShare.setOnClickListener {
            viewModel.export()
        }

        binding.btnUndo.setOnClickListener {
            viewModel.undoOverlay()
        }

        binding.btnRedo.setOnClickListener {
            viewModel.redoOverlay()
        }

        parentFragmentManager.setFragmentResultListener(
            SignaturePickerBottomSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val action = bundle.getString("action") ?: return@setFragmentResultListener
            val typeName = bundle.getString("type") ?: OverlayType.TTD.name
            val overlayType = OverlayType.valueOf(typeName)

            when (action) {
                "canvas" -> {
                    awaitingOverlayType = overlayType
                    awaitingPreviousBitmapRef = when (overlayType) {
                        OverlayType.TTD -> signatureViewModel.ttdBitmap.value
                        OverlayType.PARAF -> signatureViewModel.parafBitmap.value
                    }
                    findNavController().navigate(
                        com.wantox86.signpdf.R.id.signatureCanvasFragment,
                        bundleOf("type" to overlayType.name)
                    )
                }

                "import" -> {
                    awaitingOverlayType = overlayType
                    awaitingPreviousBitmapRef = when (overlayType) {
                        OverlayType.TTD -> signatureViewModel.ttdBitmap.value
                        OverlayType.PARAF -> signatureViewModel.parafBitmap.value
                    }
                    pendingImportType = overlayType
                    importImageLauncher.launch("image/*")
                }

                "saved" -> {
                    val savedBitmap = when (overlayType) {
                        OverlayType.TTD -> signatureViewModel.ttdBitmap.value
                        OverlayType.PARAF -> signatureViewModel.parafBitmap.value
                    }
                    if (savedBitmap != null) {
                        viewModel.addOverlay(overlayType, savedBitmap, currentPageIndex())
                    } else {
                        Snackbar.make(
                            binding.root,
                            getString(com.wantox86.signpdf.R.string.no_saved_signature),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        val pdfUri = arguments?.getString("pdfUri")
        pdfUri?.let { viewModel.loadPdf(it) }

        lifecycleScope.launchWhenStarted {
            viewModel.pages.collectLatest { pages ->
                // adapter.onPageVisible dipanggil dari dalam onBindViewHolder, yang bisa memicu
                // update pages ini secara synchronous selagi RecyclerView masih di tengah layout
                // pass (viewModelScope pakai Dispatchers.Main.immediate). notifyDataSetChanged()
                // langsung di titik itu bikin IllegalStateException "Cannot call this method
                // while RecyclerView is computing a layout or scrolling" -- post() biar nunggu
                // layout pass yang lagi jalan kelar dulu.
                binding.recyclerPdfPages.post {
                    if (_binding != null) adapter.setPages(pages)
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.isLoadingPages.collectLatest { loading ->
                binding.progressLoadingPages.visibility = if (loading) View.VISIBLE else View.GONE
                // Nambah overlay butuh bitmap halaman itu udah kerender buat nentuin ukuran
                // halaman yang bener (lihat komentar di PdfEditorViewModel.addOverlay) -- kalau
                // user scroll cepet ke halaman bawah & nambahin sign sebelum render halaman itu
                // kelar, dulu bakal ke-fallback ke ukuran tebakan yang salah (khususnya buat
                // dokumen landscape). Disable tombol tambah sampe render semua halaman kelar
                // biar kejadian itu nggak mungkin lagi.
                binding.fabAddTtd.isEnabled = !loading
                binding.fabAddParaf.isEnabled = !loading
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.overlays.collectLatest { overlays ->
                allOverlays = overlays
                adapter.setOverlays(overlays)
            }
        }

        lifecycleScope.launchWhenStarted {
            signatureViewModel.ttdBitmap.collectLatest { bitmap ->
                if (
                    awaitingOverlayType == OverlayType.TTD &&
                    bitmap != null &&
                    bitmap !== awaitingPreviousBitmapRef
                ) {
                    viewModel.addOverlay(OverlayType.TTD, bitmap, currentPageIndex())
                    awaitingOverlayType = null
                    awaitingPreviousBitmapRef = null
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            signatureViewModel.parafBitmap.collectLatest { bitmap ->
                if (
                    awaitingOverlayType == OverlayType.PARAF &&
                    bitmap != null &&
                    bitmap !== awaitingPreviousBitmapRef
                ) {
                    viewModel.addOverlay(OverlayType.PARAF, bitmap, currentPageIndex())
                    awaitingOverlayType = null
                    awaitingPreviousBitmapRef = null
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.exportState.collectLatest { state ->
                when (state) {
                    is ExportState.Idle -> hideProgress()
                    is ExportState.Loading -> showProgress()
                    is ExportState.Success -> {
                        hideProgress()
                        // Diagnostic sementara: dialog selectable/copyable nunjukin detail proses
                        // embed (config bitmap, hasil PDImageXObject, koordinat final vs batas
                        // halaman) -- buat nelusurin laporan "overlay ke-embed tapi nggak
                        // kelihatan di preview" tanpa perlu adb/logcat.
                        showExportDiagnostics(state.diagnostics)
                        findNavController().navigate(
                            com.wantox86.signpdf.R.id.action_pdfEditorFragment_to_pdfPreviewFragment,
                            bundleOf(
                                "filePath" to state.file.absolutePath,
                                "firstSignedPage" to state.firstSignedPage
                            )
                        )
                        viewModel.resetExportState()
                    }

                    is ExportState.Error -> {
                        hideProgress()
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        viewModel.resetExportState()
                    }
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.loadError.collectLatest { errorMessage ->
                if (errorMessage != null) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Error")
                        .setMessage(errorMessage)
                        .setCancelable(false)
                        .setPositiveButton("OK") { _, _ ->
                            viewModel.clearLoadError()
                            findNavController().navigateUp()
                        }
                        .show()
                }
            }
        }
    }

    private fun currentPageIndex(): Int {
        val layoutManager = binding.recyclerPdfPages.layoutManager as? LinearLayoutManager
        val index = layoutManager?.findFirstVisibleItemPosition() ?: 0
        return if (index < 0) 0 else index
    }

    private fun showExportDiagnostics(diagnostics: String) {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val textView = TextView(requireContext()).apply {
            text = diagnostics
            setPadding(padding, padding, padding, padding)
            setTextIsSelectable(true)
            textSize = 11f
        }
        val scrollView = ScrollView(requireContext()).apply { addView(textView) }
        AlertDialog.Builder(requireContext())
            .setTitle("Export diagnostics")
            .setView(scrollView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showProgress() {
        if (progressDialog?.isShowing == true) return
        progressDialog = AlertDialog.Builder(requireContext())
            .setView(ProgressBar(requireContext()))
            .setCancelable(false)
            .create()
        progressDialog?.show()
    }

    private fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onDestroyView() {
        hideProgress()
        super.onDestroyView()
        _binding = null
    }
}
