package com.wantox86.signpdf.ui.editor

import android.graphics.Bitmap
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wantox86.signpdf.databinding.ItemPdfPageBinding

class PdfPageAdapter : RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {
    private val pages = mutableListOf<Bitmap?>()
    var onPageVisible: (Int) -> Unit = {}

    fun setPages(newPages: List<Bitmap?>) {
        pages.clear()
        pages.addAll(newPages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val binding = ItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PdfPageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        onPageVisible(position)
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size

    override fun onViewRecycled(holder: PdfPageViewHolder) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    class PdfPageViewHolder(private val binding: ItemPdfPageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bitmap: Bitmap?) {
            if (bitmap == null) {
                binding.imgPdfPage.setImageDrawable(null)
                binding.viewSkeleton.visibility = View.VISIBLE
            } else {
                binding.viewSkeleton.visibility = View.GONE
                binding.imgPdfPage.setImageBitmap(bitmap)
            }
        }

        fun recycle() {
            binding.imgPdfPage.setImageDrawable(null)
        }
    }
}
