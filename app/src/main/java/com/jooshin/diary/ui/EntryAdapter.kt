package com.jooshin.diary.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.jooshin.diary.data.DiaryEntry
import com.jooshin.diary.data.dayCount
import com.jooshin.diary.data.dayIndexOf
import com.jooshin.diary.data.endDay
import com.jooshin.diary.data.isMultiDay
import com.jooshin.diary.databinding.ItemEntryBinding
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.ImageStore

class EntryAdapter(
    private val showDate: Boolean = false,
    private val onClick: (DiaryEntry) -> Unit
) : ListAdapter<DiaryEntry, EntryAdapter.VH>(DIFF) {

    /** 지금 보고 있는 날짜. 여러 날에 걸친 일정의 "N일차" 표시에 쓰인다. */
    var refDay: Long = DateUtil.today()

    inner class VH(val b: ItemEntryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = getItem(position)
        val b = holder.b
        val ctx = b.root.context

        b.entryTime.text = DateUtil.formatTimeRangeShort(
            e.dateEpochDay, e.timeMinutes, e.endDay, e.endTimeMinutes
        )
        if (showDate && !e.isMultiDay) {
            b.entryDate.visibility = android.view.View.VISIBLE
            b.entryDate.text = DateUtil.formatShortDate(e.dateEpochDay)
        } else {
            b.entryDate.visibility = android.view.View.GONE
        }

        b.entryMood.text = e.mood
        b.entryMood.visibility = if (e.mood.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

        val idx = e.dayIndexOf(refDay)
        b.entryTitle.text = e.title.ifBlank { "(제목 없음)" } +
            if (e.isMultiDay && idx > 0) "  (${idx}/${e.dayCount}일차)" else ""

        val content = e.content.trim()
        b.entryContent.text = content
        b.entryContent.visibility = if (content.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

        val tagText = if (e.tags.isEmpty()) "" else e.tags.joinToString(" ") { "#$it" }
        b.entryTags.text = tagText
        b.entryTags.visibility = if (tagText.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

        b.entryImportance.progress = e.importance
        b.entryImportanceText.text = "${e.importance}%"

        if (e.photos.isNotEmpty()) {
            b.entryThumb.visibility = android.view.View.VISIBLE
            b.entryThumb.load(ImageStore.file(ctx, e.photos.first())) {
                crossfade(true)
            }
        } else {
            b.entryThumb.visibility = android.view.View.GONE
            b.entryThumb.setImageDrawable(null)
        }

        b.root.setOnClickListener { onClick(e) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DiaryEntry>() {
            override fun areItemsTheSame(a: DiaryEntry, b: DiaryEntry) = a.id == b.id
            override fun areContentsTheSame(a: DiaryEntry, b: DiaryEntry) = a == b
        }
    }
}
