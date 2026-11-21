package com.wantox86.signpdf.ui.home

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wantox86.signpdf.R
import com.wantox86.signpdf.databinding.ItemRecentDocumentBinding
import com.wantox86.signpdf.domain.model.DocumentStatus
import com.wantox86.signpdf.domain.model.RecentDocument

class RecentDocumentAdapter(
    private val onClick: (RecentDocument) -> Unit
) : RecyclerView.Adapter<RecentDocumentAdapter.ViewHolder>() {

    private var items: List<RecentDocument> = emptyList()

    fun submitList(newItems: List<RecentDocument>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentDocumentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], isLast = position == items.lastIndex)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemRecentDocumentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(doc: RecentDocument, isLast: Boolean) {
            val context = binding.root.context
            binding.tvDocName.text = doc.fileName

            val relativeTime = DateUtils.getRelativeTimeSpanString(
                doc.lastOpenedAtMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()

            binding.tvDocMeta.text = if (doc.sizeBytes > 0L) {
                val formattedSize = Formatter.formatShortFileSize(context, doc.sizeBytes)
                "$formattedSize · $relativeTime"
            } else {
                relativeTime
            }

            when (doc.status) {
                DocumentStatus.SIGNED -> {
                    binding.tvDocStatus.setText(R.string.doc_status_signed)
                    binding.tvDocStatus.setBackgroundResource(R.drawable.bg_pill_success)
                    binding.tvDocStatus.setTextColor(
                        ContextCompat.getColor(context, R.color.sp_success_text)
                    )
                }
                DocumentStatus.DRAFT -> {
                    binding.tvDocStatus.setText(R.string.doc_status_draft)
                    binding.tvDocStatus.setBackgroundResource(R.drawable.bg_pill_warning)
                    binding.tvDocStatus.setTextColor(
                        ContextCompat.getColor(context, R.color.sp_warning_text)
                    )
                }
            }

            binding.dividerRow.visibility = if (isLast) View.GONE else View.VISIBLE
            binding.root.setOnClickListener { onClick(doc) }
        }
    }
}
