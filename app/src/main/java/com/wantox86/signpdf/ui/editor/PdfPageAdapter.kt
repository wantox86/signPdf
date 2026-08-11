package com.wantox86.signpdf.ui.editor

import android.graphics.Bitmap
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wantox86.signpdf.databinding.ItemPdfPageBinding

class PdfPageAdapter : RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {
    private val pages = mutableListOf<Bitmap?>()

    fun setPages(newPages: List<Bitmap?>) {
        // Cuma page count berubah (praktisnya sekali doang, pas load awal) yang butuh
        // notifyDataSetChanged() penuh. Update rutin sesudahnya (page dirender/di-unload)
        // notifyItemChanged() per index yang beneran ganti aja, biar nggak rebind/redraw
        // semua item yang lagi kelihatan tiap kali cuma 1 halaman yang berubah -- itu
        // penyebab kedip-kedip layar.
        if (pages.size != newPages.size) {
            pages.clear()
            pages.addAll(newPages)
            notifyDataSetChanged()
            return
        }

        for (index in newPages.indices) {
            if (pages[index] !== newPages[index]) {
                pages[index] = newPages[index]
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val binding = ItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PdfPageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
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
