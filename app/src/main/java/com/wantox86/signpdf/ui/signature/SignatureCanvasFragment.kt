package com.wantox86.signpdf.ui.signature

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.github.gcacace.signaturepad.views.SignaturePad
import com.wantox86.signpdf.R
import com.wantox86.signpdf.databinding.FragmentSignatureCanvasBinding
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.ui.common.applyBottomSystemBarPadding
import com.wantox86.signpdf.ui.common.applyTopSystemBarPadding

class SignatureCanvasFragment : Fragment() {

    private var _binding: FragmentSignatureCanvasBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SignatureViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignatureCanvasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbarSignatureCanvas.applyTopSystemBarPadding()
        binding.actionRow.applyBottomSystemBarPadding()

        val typeName = arguments?.getString("type") ?: OverlayType.TTD.name
        val type = OverlayType.valueOf(typeName)

        binding.tvCanvasTitle.text = getString(
            when (type) {
                OverlayType.TTD -> R.string.draw_signature_title
                OverlayType.PARAF -> R.string.draw_initial_title
            }
        )

        binding.btnCanvasBack.setOnClickListener {
            findNavController().navigateUp()
        }

        selectInkColor(R.color.sp_ink_black, binding.dotCanvasBlack)
        binding.dotCanvasBlack.setOnClickListener {
            selectInkColor(R.color.sp_ink_black, binding.dotCanvasBlack)
        }
        binding.dotCanvasBlue.setOnClickListener {
            selectInkColor(R.color.sp_ink_blue, binding.dotCanvasBlue)
        }
        binding.dotCanvasCrimson.setOnClickListener {
            selectInkColor(R.color.sp_ink_crimson, binding.dotCanvasCrimson)
        }

        binding.signaturePad.setOnSignedListener(object : SignaturePad.OnSignedListener {
            override fun onStartSigning() {
                binding.tvCanvasPlaceholder.visibility = View.GONE
            }

            override fun onSigned() {
                binding.btnConfirm.isEnabled = true
            }

            override fun onClear() {
                binding.tvCanvasPlaceholder.visibility = View.VISIBLE
                binding.btnConfirm.isEnabled = false
            }
        })

        binding.btnClear.setOnClickListener {
            binding.signaturePad.clear()
        }

        binding.btnConfirm.setOnClickListener {
            if (binding.signaturePad.isEmpty) return@setOnClickListener
            val bitmap = binding.signaturePad.transparentSignatureBitmap
            viewModel.savePendingBitmap(bitmap, type)
            findNavController().navigateUp()
        }
    }

    private fun selectInkColor(colorRes: Int, selectedView: View) {
        val color = ContextCompat.getColor(requireContext(), colorRes)
        binding.signaturePad.setPenColor(color)
        val dots = listOf(binding.dotCanvasBlack, binding.dotCanvasBlue, binding.dotCanvasCrimson)
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
}
