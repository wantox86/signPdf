package com.wantox86.signpdf.ui.editor

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.wantox86.signpdf.databinding.FragmentPdfEditorBinding
import com.wantox86.signpdf.domain.model.OverlayType
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

        binding.fabAddTtd.setOnClickListener {
            SignaturePickerBottomSheet.newInstance(OverlayType.TTD)
                .show(parentFragmentManager, "signature_picker_ttd")
        }

        binding.fabAddParaf.setOnClickListener {
            SignaturePickerBottomSheet.newInstance(OverlayType.PARAF)
                .show(parentFragmentManager, "signature_picker_paraf")
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
                    findNavController().navigate(
                        com.wantox86.signpdf.R.id.signatureCanvasFragment,
                        bundleOf("type" to overlayType.name)
                    )
                }

                "import" -> {
                    pendingImportType = overlayType
                    importImageLauncher.launch("image/*")
                }
            }
        }

        val pdfUri = arguments?.getString("pdfUri")
        pdfUri?.let { viewModel.loadPdf(it) }

        lifecycleScope.launchWhenStarted {
            viewModel.pages.collectLatest { pages ->
                adapter.setPages(pages)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
