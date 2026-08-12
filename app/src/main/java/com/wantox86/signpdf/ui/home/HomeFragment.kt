package com.wantox86.signpdf.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.wantox86.signpdf.databinding.FragmentHomeBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private val openPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.openPdf(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnOpenPdf.setOnClickListener {
            openPdfLauncher.launch(arrayOf("application/pdf"))
        }
        // viewLifecycleOwner.lifecycleScope (bukan lifecycleScope Fragment biasa) + repeatOnLifecycle:
        // lifecycleScope milik Fragment itu sendiri BERTAHAN lintas re-create view (balik dari
        // back-stack), jadi launchWhenStarted lama nggak pernah ke-cancel -- tiap kali Home
        // kelihatan lagi, collector BARU numpuk di atas yang lama. Sekali "Open PDF" ke-tap,
        // beberapa collector nyala bareng, masing-masing manggil navigate() buat event yang
        // sama -- yang kedua gagal karena destination udah pindah (IllegalArgumentException
        // "action ... cannot be found from the current destination"). repeatOnLifecycle terikat
        // viewLifecycleOwner, otomatis cancel bersih tiap onDestroyView.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.openPdfEvent.collectLatest { uri ->
                    val bundle = Bundle().apply { putString("pdfUri", uri.toString()) }
                    findNavController().navigate(
                        com.wantox86.signpdf.R.id.action_homeFragment_to_pdfEditorFragment,
                        bundle
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
