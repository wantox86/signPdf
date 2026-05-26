package com.wantox86.signpdf.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.wantox86.signpdf.databinding.FragmentPdfEditorBinding
import kotlinx.coroutines.flow.collectLatest

class PdfEditorFragment : Fragment() {
    private var _binding: FragmentPdfEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PdfEditorViewModel by viewModels()
    private val adapter = PdfPageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerPdfPages.adapter = adapter
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
