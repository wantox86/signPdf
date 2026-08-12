package com.wantox86.signpdf.ui.signature

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.wantox86.signpdf.databinding.FragmentSignatureCanvasBinding
import com.wantox86.signpdf.domain.model.OverlayType

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

        val typeName = arguments?.getString("type") ?: OverlayType.TTD.name
        val type = OverlayType.valueOf(typeName)

        binding.btnClear.setOnClickListener {
            binding.signaturePad.clear()
        }

        binding.btnConfirm.setOnClickListener {
            val bitmap = binding.signaturePad.transparentSignatureBitmap
            viewModel.savePendingBitmap(bitmap, type)
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
