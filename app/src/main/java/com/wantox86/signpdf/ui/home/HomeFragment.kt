package com.wantox86.signpdf.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
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
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.RecentDocument
import com.wantox86.signpdf.domain.model.SyncState
import com.wantox86.signpdf.ui.common.applyBottomSystemBarPadding
import com.wantox86.signpdf.ui.common.applyTopSystemBarPadding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private val recentAdapter = RecentDocumentAdapter { doc -> onRecentDocumentClicked(doc) }

    private val openPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Persist read permission so the PDF can be reopened from Recent Documents later.
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
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
        binding.toolbarHome.applyTopSystemBarPadding()
        binding.scrollHome.applyBottomSystemBarPadding()
        binding.recyclerRecentDocuments.adapter = recentAdapter

        binding.btnOpenPdf.setOnClickListener {
            openPdfLauncher.launch(arrayOf("application/pdf"))
        }
        binding.pillAccount.setOnClickListener {
            onAccountPillClicked()
        }
        binding.btnManageSignatures.setOnClickListener {
            showManageSignaturesDialog()
        }
        binding.btnNewSignature.setOnClickListener {
            showManageSignaturesDialog()
        }
        binding.cardSavedTtd.setOnClickListener {
            openSignatureCanvas(OverlayType.TTD)
        }
        binding.cardSavedParaf.setOnClickListener {
            openSignatureCanvas(OverlayType.PARAF)
        }

        // viewLifecycleOwner.lifecycleScope (bukan lifecycleScope Fragment biasa) + repeatOnLifecycle:
        // lifecycleScope milik Fragment itu sendiri BERTAHAN lintas re-create view (balik dari
        // back-stack), jadi launchWhenStarted lama nggak pernah ke-cancel -- tiap kali Home
        // kelihatan lagi, collector BARU numpuk di atas yang lama. repeatOnLifecycle terikat
        // viewLifecycleOwner, otomatis cancel bersih tiap onDestroyView.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.openPdfEvent.collectLatest { uri ->
                        val bundle = bundleOf("pdfUri" to uri.toString())
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
                    viewModel.ttdBitmap.collectLatest { bitmap ->
                        binding.cardSavedTtd.visibility = if (bitmap != null) View.VISIBLE else View.GONE
                        binding.imgSavedTtd.setImageBitmap(bitmap)
                    }
                }
                launch {
                    viewModel.parafBitmap.collectLatest { bitmap ->
                        binding.cardSavedParaf.visibility = if (bitmap != null) View.VISIBLE else View.GONE
                        binding.imgSavedParaf.setImageBitmap(bitmap)
                    }
                }
                launch {
                    viewModel.recentDocuments.collectLatest { docs -> renderRecentDocuments(docs) }
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

    override fun onResume() {
        super.onResume()
        viewModel.refreshSavedSignatures()
    }

    private fun renderAuthState(state: AuthState) {
        binding.tvAccountPill.text = when (state) {
            is AuthState.Guest -> getString(R.string.home_account_guest_pill)
            is AuthState.Authenticated -> getString(R.string.status_authenticated, state.username)
        }
    }

    private fun renderSyncState(state: SyncState) {
        if (state is SyncState.Failed) {
            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun renderRecentDocuments(docs: List<RecentDocument>) {
        binding.sectionRecentDocuments.visibility = if (docs.isEmpty()) View.GONE else View.VISIBLE
        binding.tvRecentCount.text = if (docs.size == 1) {
            getString(R.string.home_recent_files_count_one)
        } else {
            getString(R.string.home_recent_files_count_many, docs.size)
        }
        recentAdapter.submitList(docs)
    }

    private fun onAccountPillClicked() {
        when (val state = viewModel.authState.value) {
            is AuthState.Guest -> {
                findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
            }
            is AuthState.Authenticated -> {
                val syncLabel = when (val sync = viewModel.syncState.value) {
                    is SyncState.Idle -> ""
                    is SyncState.Syncing -> "\n${getString(R.string.sync_state_syncing)}"
                    is SyncState.Synced -> "\n${getString(R.string.sync_state_synced)}"
                    is SyncState.Failed -> "\n${sync.message}"
                }
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.account_dialog_title)
                    .setMessage("${state.username}$syncLabel")
                    .setPositiveButton(R.string.sync_button) { _, _ -> viewModel.sync() }
                    .setNegativeButton(R.string.logout_button) { _, _ -> viewModel.logout() }
                    .setNeutralButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun showManageSignaturesDialog() {
        val options = arrayOf(
            getString(R.string.overlay_type_ttd),
            getString(R.string.overlay_type_paraf)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.manage_signatures_dialog_title)
            .setItems(options) { _, which ->
                val type = if (which == 0) OverlayType.TTD else OverlayType.PARAF
                openSignatureCanvas(type)
            }
            .show()
    }

    private fun openSignatureCanvas(type: OverlayType) {
        val bundle = bundleOf("type" to type.name)
        findNavController().navigate(
            R.id.action_homeFragment_to_signatureCanvasFragment,
            bundle
        )
    }

    private fun onRecentDocumentClicked(doc: RecentDocument) {
        val uri = Uri.parse(doc.uri)
        val accessible = runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)

        if (accessible) {
            viewModel.openRecentDocument(doc)
        } else {
            viewModel.removeRecentDocument(doc.uri)
            Snackbar.make(binding.root, R.string.error_recent_file_unavailable, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showMigrationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.migration_dialog_title)
            .setMessage(R.string.migration_dialog_message)
            .setPositiveButton(R.string.migration_dialog_upload) { _, _ ->
                viewModel.confirmMigrationUpload()
            }
            .setNegativeButton(R.string.migration_dialog_skip) { _, _ ->
                viewModel.dismissMigrationPrompt()
            }
            .setOnCancelListener { viewModel.dismissMigrationPrompt() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
