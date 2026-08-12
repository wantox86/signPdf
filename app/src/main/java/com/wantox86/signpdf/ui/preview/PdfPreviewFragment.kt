package com.wantox86.signpdf.ui.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.wantox86.signpdf.databinding.FragmentPdfPreviewBinding
import com.wantox86.signpdf.ui.editor.PdfPageAdapter
import com.wantox86.signpdf.ui.share.ShareHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class PdfPreviewFragment : Fragment() {
    private var _binding: FragmentPdfPreviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PdfPreviewViewModel by viewModels()
    private val adapter = PdfPageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerPreviewPages.adapter = adapter

        binding.btnBackToEditor.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnShare.setOnClickListener {
            val filePath = arguments?.getString("filePath") ?: return@setOnClickListener
            ShareHelper.sharePdf(requireContext(), File(filePath))
        }

        val filePath = arguments?.getString("filePath")
        if (filePath == null) {
            Snackbar.make(binding.root, getString(com.wantox86.signpdf.R.string.error_preview_load), Snackbar.LENGTH_LONG).show()
            return
        }
        viewModel.loadPreview(filePath)

        // viewLifecycleOwner.lifecycleScope + repeatOnLifecycle, bukan lifecycleScope Fragment +
        // launchWhenStarted -- lihat komentar senada di PdfEditorFragment, pola yang sama bikin
        // collector numpuk tiap Fragment ini kelihatan lagi lewat back-stack.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loadState.collectLatest { state ->
                    when (state) {
                        is PreviewLoadState.Loading -> {
                            binding.progressPreviewLoading.visibility = View.VISIBLE
                        }

                        is PreviewLoadState.Ready -> {
                            binding.progressPreviewLoading.visibility = View.GONE
                            adapter.setPages(state.pages)
                            // Langsung ke halaman yang ada TTD/paraf-nya -- kalau ditandatangan di
                            // halaman bawah, jangan biarin preview mulai dari halaman 1 (kesannya
                            // "kok nggak ada", padahal cuma perlu scroll).
                            val firstSignedPage = arguments?.getInt("firstSignedPage", 0) ?: 0
                            if (firstSignedPage in state.pages.indices) {
                                binding.recyclerPreviewPages.scrollToPosition(firstSignedPage)
                            }
                        }

                        is PreviewLoadState.Error -> {
                            binding.progressPreviewLoading.visibility = View.GONE
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
