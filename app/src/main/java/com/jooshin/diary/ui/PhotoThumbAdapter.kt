package com.jooshin.diary.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.jooshin.diary.databinding.ItemPhotoThumbBinding
import com.jooshin.diary.util.ImageStore

/** 편집 화면의 첨부 사진 썸네일 목록 (삭제 버튼 포함). */
class PhotoThumbAdapter(
    private val items: MutableList<String>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PhotoThumbAdapter.VH>() {

    inner class VH(val b: ItemPhotoThumbBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPhotoThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val name = items[position]
        val ctx = holder.b.root.context
        holder.b.thumbImage.load(ImageStore.file(ctx, name)) { crossfade(true) }
        holder.b.thumbRemove.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onRemove(pos)
        }
    }
}
