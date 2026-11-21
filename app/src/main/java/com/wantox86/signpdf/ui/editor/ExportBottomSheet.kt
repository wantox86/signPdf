package com.wantox86.signpdf.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wantox86.signpdf.R
import com.wantox86.signpdf.databinding.BottomsheetExportBinding
import com.wantox86.signpdf.ui.common.applyBottomSystemBarPadding
import com.wantox86.signpdf.ui.share.ShareHelper
import java.io.File
import java.util.Locale

class ExportBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetExportBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_SignPDF_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyBottomSystemBarPadding()

        val filePath = requireArguments().getString(ARG_FILE_PATH).orEmpty()
        val firstSignedPage = requireArguments().getInt(ARG_FIRST_SIGNED_PAGE, 0)
        val exportedFile = File(filePath)
        val displayName = exportedFile.name.ifBlank { "signed.pdf" }
        val formattedSize = formatFileSize(exportedFile.length())

        binding.tvExportSubtitle.text = getString(
            R.string.export_sheet_meta,
            displayName,
            formattedSize
        )

        binding.btnCloseExportSheet.setOnClickListener { dismiss() }

        binding.cardPreviewPdf.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf(
                    RESULT_ACTION to ACTION_PREVIEW,
                    RESULT_FILE_PATH to filePath,
                    RESULT_FIRST_SIGNED_PAGE to firstSignedPage
                )
            )
            dismiss()
        }

        binding.cardSharePdf.setOnClickListener {
            if (exportedFile.exists()) {
                ShareHelper.sharePdf(requireContext(), exportedFile)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "export_bottom_sheet"
        const val RESULT_KEY = "export_bottom_sheet_result"
        const val RESULT_ACTION = "action"
        const val RESULT_FILE_PATH = "filePath"
        const val RESULT_FIRST_SIGNED_PAGE = "firstSignedPage"
        const val ACTION_PREVIEW = "preview"

        private const val ARG_FILE_PATH = "filePath"
        private const val ARG_FIRST_SIGNED_PAGE = "firstSignedPage"

        fun newInstance(filePath: String, firstSignedPage: Int): ExportBottomSheet {
            return ExportBottomSheet().apply {
                arguments = bundleOf(
                    ARG_FILE_PATH to filePath,
                    ARG_FIRST_SIGNED_PAGE to firstSignedPage
                )
            }
        }

        internal fun formatFileSize(bytes: Long): String {
            val safeBytes = bytes.coerceAtLeast(0L)
            return when {
                safeBytes < 1024L -> "$safeBytes B"
                safeBytes < 1024L * 1024L -> {
                    val kb = safeBytes / 1024.0
                    if (kb >= 100.0) String.format(Locale.US, "%.0f KB", kb)
                    else String.format(Locale.US, "%.1f KB", kb)
                }

                else -> {
                    val mb = safeBytes / (1024.0 * 1024.0)
                    String.format(Locale.US, "%.2f MB", mb)
                }
            }
        }
    }
}
