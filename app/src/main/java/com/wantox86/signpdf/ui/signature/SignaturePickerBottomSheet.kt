package com.wantox86.signpdf.ui.signature

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wantox86.signpdf.databinding.BottomsheetSignaturePickerBinding
import com.wantox86.signpdf.domain.model.OverlayType

class SignaturePickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetSignaturePickerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetSignaturePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val typeName = requireArguments().getString(ARG_OVERLAY_TYPE) ?: OverlayType.TTD.name
        val overlayType = OverlayType.valueOf(typeName)

        binding.tvLabelType.text = overlayType.name

        binding.btnDrawOnScreen.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf("action" to "canvas", "type" to overlayType.name)
            )
            dismiss()
        }

        binding.btnImportImage.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf("action" to "import", "type" to overlayType.name)
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "signature_picker"
        private const val ARG_OVERLAY_TYPE = "overlayType"

        fun newInstance(overlayType: OverlayType): SignaturePickerBottomSheet {
            return SignaturePickerBottomSheet().apply {
                arguments = bundleOf(ARG_OVERLAY_TYPE to overlayType.name)
            }
        }
    }
}
