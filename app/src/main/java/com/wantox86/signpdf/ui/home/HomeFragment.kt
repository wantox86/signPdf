package com.wantox86.signpdf.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.wantox86.signpdf.R
import com.wantox86.signpdf.databinding.FragmentHomeBinding
import com.wantox86.signpdf.domain.model.AuthState
import com.wantox86.signpdf.domain.model.SyncState
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
        binding.btnLoginStatus.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
        }
        binding.btnSyncStatus.setOnClickListener {
            viewModel.sync()
        }
        binding.btnLogoutStatus.setOnClickListener {
            viewModel.logout()
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
                launch {
                    viewModel.openPdfEvent.collectLatest { uri ->
                        val bundle = Bundle().apply { putString("pdfUri", uri.toString()) }
                        findNavController().navigate(
                            R.id.action_homeFragment_to_pdfEditorFragment,
                            bundle
                        )
                    }
                }
                launch {
                    viewModel.authState.collectLatest { state -> renderAuthState(state) }
                }
                launch {
                    viewModel.syncState.collectLatest { state -> renderSyncState(state) }
                }
                launch {
                    viewModel.migrationPrompt.collectLatest { types ->
                        if (types != null) showMigrationDialog()
                    }
                }
                launch {
                    viewModel.sessionExpiredEvent.collectLatest { expired ->
                        if (expired) {
                            Snackbar.make(binding.root, R.string.error_session_expired, Snackbar.LENGTH_LONG).show()
                            viewModel.consumeSessionExpiredEvent()
                        }
                    }
                }
            }
        }
    }

    private fun renderAuthState(state: AuthState) {
        val authenticated = state is AuthState.Authenticated
        binding.btnLoginStatus.visibility = if (authenticated) View.GONE else View.VISIBLE
        binding.btnSyncStatus.visibility = if (authenticated) View.VISIBLE else View.GONE
        binding.btnLogoutStatus.visibility = if (authenticated) View.VISIBLE else View.GONE
        binding.tvSyncStatus.visibility = if (authenticated) View.VISIBLE else View.GONE
        binding.tvAuthStatus.text = when (state) {
            is AuthState.Guest -> getString(R.string.status_guest)
            is AuthState.Authenticated -> getString(R.string.status_authenticated, state.username)
        }
    }

    private fun renderSyncState(state: SyncState) {
        binding.tvSyncStatus.text = when (state) {
            is SyncState.Idle -> ""
            is SyncState.Syncing -> getString(R.string.sync_state_syncing)
            is SyncState.Synced -> getString(R.string.sync_state_synced)
            is SyncState.Failed -> state.message
        }
        if (state is SyncState.Failed) {
            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
        }
    }

    // Doesn't enumerate which slot(s) (TTD/PARAF) in the dialog copy -- V1 scope is at most
    // one of each (plan decision #6), so "your local signature" already covers either/both.
    private fun showMigrationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.migration_dialog_title)
            .setMessage(R.string.migration_dialog_message)
            .setPositiveButton(R.string.migration_dialog_upload) { _, _ -> viewModel.confirmMigrationUpload() }
            .setNegativeButton(R.string.migration_dialog_skip) { _, _ -> viewModel.dismissMigrationPrompt() }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
