package com.wantox86.signpdf.ui.editor

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.snackbar.Snackbar
import com.wantox86.signpdf.databinding.FragmentPdfEditorBinding
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.SignatureOverlay
import com.wantox86.signpdf.ui.signature.SignaturePickerBottomSheet
import com.wantox86.signpdf.ui.signature.SignatureViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    private var isSignatureMenuExpanded = false

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
        // notifyItemChanged() default-nya mainin change-animation (crossfade ke ViewHolder
        // pengganti) -- kalau itu kejadian di item yang overlay-nya lagi disentuh, view penerima
        // touch di-swap di tengah gesture dan drag-nya putus. Matikan biar rebind terjadi
        // in-place di holder yang sama.
        (binding.recyclerPdfPages.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        // Overlay sekarang jadi bagian dari tiap item halaman (lihat PdfPageAdapter /
        // SignatureOverlayView) -- callback ini udah dikasih tau pageIndex halaman yang bener
        // dari adapter, nggak perlu nebak/remap pakai currentPageIndex() lagi kayak desain lama
        // (yang jadi sumber bug overlay ke-taruh di halaman salah pas scroll).
        adapter.onOverlaysChangedForPage = { pageIndex, overlaysForThatPage ->
            val merged = allOverlays.filter { it.pageIndex != pageIndex } + overlaysForThatPage
            viewModel.updateOverlays(merged)
        }

        binding.fabAddSignature.setOnClickListener {
            toggleSignatureMenu()
        }

        binding.fabAddTtd.setOnClickListener {
            toggleSignatureMenu()
            SignaturePickerBottomSheet.newInstance(
                overlayType = OverlayType.TTD,
                hasSavedSignature = signatureViewModel.ttdBitmap.value != null
            )
                .show(parentFragmentManager, "signature_picker_ttd")
        }

        binding.fabAddParaf.setOnClickListener {
            toggleSignatureMenu()
            SignaturePickerBottomSheet.newInstance(
                overlayType = OverlayType.PARAF,
                hasSavedSignature = signatureViewModel.parafBitmap.value != null
            )
                .show(parentFragmentManager, "signature_picker_paraf")
        }

        binding.btnSaveAndShare.setOnClickListener {
            viewModel.export()
        }

        binding.toolbarEditor.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.toolbarEditor.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.wantox86.signpdf.R.id.action_undo -> {
                    viewModel.undoOverlay()
                    true
                }

                com.wantox86.signpdf.R.id.action_redo -> {
                    viewModel.redoOverlay()
                    true
                }

                else -> false
            }
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

        // viewLifecycleOwner.lifecycleScope + repeatOnLifecycle (bukan lifecycleScope Fragment +
        // launchWhenStarted): lifecycleScope milik Fragment BERTAHAN lintas re-create view (mis.
        // balik dari back-stack), jadi launchWhenStarted lama nggak pernah ke-cancel -- tiap kali
        // Fragment ini kelihatan lagi, collector BARU numpuk di atas yang lama. Beberapa
        // collector aktif bareng brarti satu event (klik tombol, hasil signature, dll) bisa
        // ke-proses berkali-kali (contoh nyata: navigate() dobel ke NavController, yang kedua
        // gagal dengan IllegalArgumentException karena destination udah pindah). repeatOnLifecycle
        // terikat viewLifecycleOwner, otomatis cancel bersih tiap onDestroyView.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.documentTitle.collectLatest { title ->
                        binding.tvDocumentTitle.text = title
                    }
                }

                launch {
                    viewModel.pages.collectLatest { pages ->
                        // adapter.onPageVisible dipanggil dari dalam onBindViewHolder, yang bisa
                        // memicu update pages ini secara synchronous selagi RecyclerView masih di
                        // tengah layout pass (viewModelScope pakai Dispatchers.Main.immediate).
                        // notifyDataSetChanged() langsung di titik itu bikin IllegalStateException
                        // "Cannot call this method while RecyclerView is computing a layout or
                        // scrolling" -- post() biar nunggu layout pass yang lagi jalan kelar dulu.
                        binding.recyclerPdfPages.post {
                            if (_binding != null) adapter.setPages(pages)
                        }
                    }
                }

                launch {
                    viewModel.isLoadingPages.collectLatest { loading ->
                        binding.progressLoadingPages.visibility = if (loading) View.VISIBLE else View.GONE
                        // Nambah overlay butuh bitmap halaman itu udah kerender buat nentuin
                        // ukuran halaman yang bener (lihat komentar di
                        // PdfEditorViewModel.addOverlay) -- kalau user scroll cepet ke halaman
                        // bawah & nambahin sign sebelum render halaman itu kelar, dulu bakal
                        // ke-fallback ke ukuran tebakan yang salah (khususnya buat dokumen
                        // landscape). Disable tombol tambah sampe render semua halaman kelar
                        // biar kejadian itu nggak mungkin lagi.
                        binding.fabAddTtd.isEnabled = !loading
                        binding.fabAddParaf.isEnabled = !loading
                    }
                }

                launch {
                    viewModel.overlays.collectLatest { overlays ->
                        allOverlays = overlays
                        adapter.setOverlays(overlays)
                    }
                }

                launch {
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

                launch {
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

                launch {
                    viewModel.exportState.collectLatest { state ->
                        when (state) {
                            is ExportState.Idle -> hideProgress()
                            is ExportState.Loading -> showProgress()
                            is ExportState.Success -> {
                                hideProgress()
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

                launch {
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
        }
    }

    // Halaman target buat nambah TTD/paraf = halaman yang PALING DOMINAN kelihatan di layar,
    // bukan findFirstVisibleItemPosition() -- yang "first visible" bisa halaman sebelumnya yang
    // cuma nongol beberapa px di ujung atas layar, bikin TTD ketambah ke halaman yang salah
    // (persis keluhan "muncul di page sebelumnya").
    private fun currentPageIndex(): Int {
        val layoutManager = binding.recyclerPdfPages.layoutManager as? LinearLayoutManager ?: return 0
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first < 0) return 0

        val recyclerHeight = binding.recyclerPdfPages.height
        var bestIndex = first
        var bestVisibleHeight = -1
        for (index in first..last) {
            val itemView = layoutManager.findViewByPosition(index) ?: continue
            val visibleTop = maxOf(itemView.top, 0)
            val visibleBottom = minOf(itemView.bottom, recyclerHeight)
            val visibleHeight = visibleBottom - visibleTop
            if (visibleHeight > bestVisibleHeight) {
                bestVisibleHeight = visibleHeight
                bestIndex = index
            }
        }
        return bestIndex
    }

    // Speed-dial: fab_add_signature cuma toggle visibility+animasi dua FAB yang udah ada
    // (fab_add_ttd/fab_add_paraf) -- listener asli keduanya nggak diubah sama sekali, cuma
    // ditambah pemanggilan toggle ini di awal biar menu auto-collapse begitu salah satu dipilih.
    private fun toggleSignatureMenu() {
        isSignatureMenuExpanded = !isSignatureMenuExpanded
        val targets = listOf(binding.fabAddTtd, binding.fabAddParaf)
        targets.forEach { fab ->
            if (isSignatureMenuExpanded) {
                fab.visibility = View.VISIBLE
                fab.alpha = 0f
                fab.scaleX = 0f
                fab.scaleY = 0f
                fab.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
            } else {
                fab.animate()
                    .alpha(0f)
                    .scaleX(0f)
                    .scaleY(0f)
                    .setDuration(150)
                    .withEndAction { fab.visibility = View.GONE }
                    .start()
            }
        }
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
