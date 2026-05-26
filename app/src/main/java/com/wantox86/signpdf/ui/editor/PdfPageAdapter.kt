package com.wantox86.signpdf.ui.editor

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wantox86.signpdf.databinding.ItemPdfPageBinding

class PdfPageAdapter : RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {
    private val pages = mutableListOf<Bitmap>()

    fun setPages(newPages: List<Bitmap>) {
        pages.clear()
        pages.addAll(newPages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val binding = ItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PdfPageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size

    class PdfPageViewHolder(private val binding: ItemPdfPageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bitmap: Bitmap) {
            binding.imgPdfPage.setImageBitmap(bitmap)
        }
    }
}
