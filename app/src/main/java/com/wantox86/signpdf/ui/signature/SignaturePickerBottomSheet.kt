package com.wantox86.signpdf.ui.signature

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.gcacace.signaturepad.views.SignaturePad
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wantox86.signpdf.R
import com.wantox86.signpdf.databinding.BottomsheetSignaturePickerBinding
import com.wantox86.signpdf.domain.model.OverlayType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SignaturePickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetSignaturePickerBinding? = null
    private val binding get() = _binding!!
    private val signatureViewModel: SignatureViewModel by activityViewModels()

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

        binding.tvSheetTitle.text = getString(
            when (overlayType) {
                OverlayType.TTD -> R.string.add_signature
                OverlayType.PARAF -> R.string.add_paraf
            }
        )
        binding.tvDrawTitle.text = getString(
            when (overlayType) {
                OverlayType.TTD -> R.string.draw_signature_title
                OverlayType.PARAF -> R.string.draw_initial_title
            }
        )

        binding.btnCloseSheet.setOnClickListener { dismiss() }

        // 1-tap place from saved TTD or PARAF slots
        binding.cardSheetSavedTtd.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf("action" to "saved", "type" to OverlayType.TTD.name)
            )
            dismiss()
        }

        binding.cardSheetSavedParaf.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf("action" to "saved", "type" to OverlayType.PARAF.name)
            )
            dismiss()
        }

        // Switch to inline Draw mode
        binding.cardDrawOnScreen.setOnClickListener {
            showDrawMode(true)
        }

        binding.cardImportImage.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf("action" to "import", "type" to overlayType.name)
            )
            dismiss()
        }

        // Inline Draw mode controls
        binding.btnDrawBack.setOnClickListener {
            showDrawMode(false)
        }

        selectInkColor(R.color.sp_ink_black, binding.dotInkBlack)
        binding.dotInkBlack.setOnClickListener {
            selectInkColor(R.color.sp_ink_black, binding.dotInkBlack)
        }
        binding.dotInkBlue.setOnClickListener {
            selectInkColor(R.color.sp_ink_blue, binding.dotInkBlue)
        }
        binding.dotInkCrimson.setOnClickListener {
            selectInkColor(R.color.sp_ink_crimson, binding.dotInkCrimson)
        }

        binding.signaturePadInline.setOnSignedListener(object : SignaturePad.OnSignedListener {
            override fun onStartSigning() {
                binding.tvDrawPlaceholder.visibility = View.GONE
            }

            override fun onSigned() {
                binding.btnUseSignature.isEnabled = true
            }

            override fun onClear() {
                binding.tvDrawPlaceholder.visibility = View.VISIBLE
                binding.btnUseSignature.isEnabled = false
            }
        })

        binding.btnDrawClear.setOnClickListener {
            binding.signaturePadInline.clear()
        }

        binding.btnUseSignature.setOnClickListener {
            if (binding.signaturePadInline.isEmpty) return@setOnClickListener
            val bitmap = binding.signaturePadInline.transparentSignatureBitmap
            binding.btnUseSignature.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                signatureViewModel.saveBitmapNow(bitmap, overlayType)
                parentFragmentManager.setFragmentResult(
                    RESULT_KEY,
                    bundleOf("action" to "saved", "type" to overlayType.name)
                )
                dismiss()
            }
        }

        // Live saved signature previews (2 slots: TTD & PARAF)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    signatureViewModel.ttdBitmap.collectLatest { bitmap ->
                        binding.cardSheetSavedTtd.visibility =
                            if (bitmap != null) View.VISIBLE else View.GONE
                        binding.imgSheetSavedTtd.setImageBitmap(bitmap)
                        updateSavedSectionVisibility()
                    }
                }
                launch {
                    signatureViewModel.parafBitmap.collectLatest { bitmap ->
                        binding.cardSheetSavedParaf.visibility =
                            if (bitmap != null) View.VISIBLE else View.GONE
                        binding.imgSheetSavedParaf.setImageBitmap(bitmap)
                        updateSavedSectionVisibility()
                    }
                }
            }
        }
    }

    private fun updateSavedSectionVisibility() {
        val hasAny = signatureViewModel.ttdBitmap.value != null ||
            signatureViewModel.parafBitmap.value != null
        binding.sectionUseSaved.visibility = if (hasAny) View.VISIBLE else View.GONE
    }

    private fun showDrawMode(drawMode: Boolean) {
        binding.layoutModePicker.visibility = if (drawMode) View.GONE else View.VISIBLE
        binding.layoutModeDraw.visibility = if (drawMode) View.VISIBLE else View.GONE
        // Disable vertical sheet dragging while drawing so downward strokes don't dismiss the sheet.
        val behavior = (dialog as? BottomSheetDialog)?.behavior
        behavior?.isDraggable = !drawMode
        if (drawMode) {
            behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun selectInkColor(colorRes: Int, selectedView: View) {
        val color = ContextCompat.getColor(requireContext(), colorRes)
        binding.signaturePadInline.setPenColor(color)
        val dots = listOf(binding.dotInkBlack, binding.dotInkBlue, binding.dotInkCrimson)
        dots.forEach { dot ->
            val selected = dot === selectedView
            dot.scaleX = if (selected) 1.18f else 0.92f
            dot.scaleY = if (selected) 1.18f else 0.92f
            dot.alpha = if (selected) 1f else 0.55f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "signature_picker"
        private const val ARG_OVERLAY_TYPE = "overlayType"
        private const val ARG_HAS_SAVED_SIGNATURE = "hasSavedSignature"

        fun newInstance(
            overlayType: OverlayType,
            hasSavedSignature: Boolean = false
        ): SignaturePickerBottomSheet {
            return SignaturePickerBottomSheet().apply {
                arguments = bundleOf(
                    ARG_OVERLAY_TYPE to overlayType.name,
                    ARG_HAS_SAVED_SIGNATURE to hasSavedSignature
                )
            }
        }
    }
}
