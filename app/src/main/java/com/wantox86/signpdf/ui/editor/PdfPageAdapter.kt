package com.wantox86.signpdf.ui.editor

import android.graphics.Bitmap
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wantox86.signpdf.databinding.ItemPdfPageBinding
import com.wantox86.signpdf.domain.model.SignatureOverlay

class PdfPageAdapter : RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {
    private val pages = mutableListOf<Bitmap?>()

    // Overlay sekarang jadi bagian dari tiap item halaman (bukan satu view fullscreen terpisah
    // kayak sebelumnya) -- overlaysByPage default kosong dipakai apa adanya buat layar Preview
    // (read-only, TTD/paraf udah ke-bake jadi bagian bitmap hasil export, nggak perlu overlay
    // interaktif lagi).
    private var overlaysByPage: Map<Int, List<SignatureOverlay>> = emptyMap()

    // (pageIndex, overlay list milik halaman itu abis di-drag/resize/hapus) -- Fragment yang
    // gabungin balik ke daftar overlay lengkap. pageIndex overlay yang dikirim balik ke sini
    // udah pasti benar (nempel ke halaman tempat dia digambar), nggak perlu remap lagi.
    var onOverlaysChangedForPage: (Int, List<SignatureOverlay>) -> Unit = { _, _ -> }

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

    fun setOverlays(allOverlays: List<SignatureOverlay>) {
        val newMap = allOverlays.groupBy { it.pageIndex }
        if (newMap == overlaysByPage) return
        val changedPages = (newMap.keys + overlaysByPage.keys)
            .filter { newMap[it] != overlaysByPage[it] }
        overlaysByPage = newMap
        changedPages.forEach { pageIndex ->
            if (pageIndex in pages.indices) notifyItemChanged(pageIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val binding = ItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PdfPageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        // onOverlaysChanged di-reassign tiap bind (bukan sekali di init) biar closure-nya
        // selalu nutup ke `position` yang lagi valid sekarang -- ViewHolder di-reuse RecyclerView
        // buat posisi berbeda-beda pas scroll, jadi nggak boleh capture posisi lama.
        holder.binding.signatureOverlayView.onOverlaysChanged = { newOverlaysForThisPage ->
            onOverlaysChangedForPage(position, newOverlaysForThisPage)
        }
        holder.bind(pages[position], overlaysByPage[position].orEmpty())
    }

    override fun getItemCount(): Int = pages.size

    override fun onViewRecycled(holder: PdfPageViewHolder) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    class PdfPageViewHolder(val binding: ItemPdfPageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bitmap: Bitmap?, overlaysForPage: List<SignatureOverlay>) {
            if (bitmap == null) {
                binding.imgPdfPage.setImageDrawable(null)
                binding.viewSkeleton.visibility = View.VISIBLE
                binding.signatureOverlayView.setOverlays(emptyList())
            } else {
                binding.viewSkeleton.visibility = View.GONE
                binding.imgPdfPage.setImageBitmap(bitmap)
                binding.signatureOverlayView.setPageBitmapSize(bitmap.width, bitmap.height)
                binding.signatureOverlayView.setOverlays(overlaysForPage)
            }
        }

        fun recycle() {
            binding.imgPdfPage.setImageDrawable(null)
        }
    }
}
