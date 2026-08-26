package com.jooshin.diary.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.jooshin.diary.R
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.data.DiaryEntry
import com.jooshin.diary.data.dayCount
import com.jooshin.diary.data.dayIndexOf
import com.jooshin.diary.data.endDay
import com.jooshin.diary.data.isMultiDay
import com.jooshin.diary.ui.MainActivity
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.Palette
import java.time.LocalTime

class DayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        DayFactory(applicationContext, intent)
}

private sealed class DayRow {
    data class AllDayHeader(val day: Long) : DayRow()
    data class HourHeader(val day: Long, val hour: Int, val isNow: Boolean) : DayRow()
    data class Item(val entry: DiaryEntry, val day: Long) : DayRow()
    data class Blank(val day: Long) : DayRow()
}

/**
 * 일 위젯: 0시 ~ 23시를 1시간 간격으로 쭉 보여준다.
 * 리스트라서 위아래로 드래그하면 24시간 전체를 볼 수 있다.
 */
private class DayFactory(
    private val ctx: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private var rows: List<DayRow> = emptyList()
    private var pal: Palette = Palette.of(ctx)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        pal = Palette.of(ctx)
        val day = WidgetState.getAnchor(ctx, appWidgetId, DateUtil.today())
        val items = AppDatabase.get(ctx).diaryDao().getForDaySync(day)

        // 종일 일정 + 앞선 날짜에서 이어지는 일정
        val allDay = items.filter { it.dateEpochDay < day || it.timeMinutes < 0 }
        val timed = items.filter { it.dateEpochDay == day && it.timeMinutes >= 0 }
        val byHour = timed.groupBy { (it.timeMinutes / 60).coerceIn(0, 23) }

        val nowHour = if (day == DateUtil.today()) LocalTime.now().hour else -1

        val list = ArrayList<DayRow>(24 * 2 + 4)
        if (allDay.isNotEmpty()) {
            list.add(DayRow.AllDayHeader(day))
            allDay.forEach { list.add(DayRow.Item(it, day)) }
        }
        for (h in 0..23) {
            list.add(DayRow.HourHeader(day, h, h == nowHour))
            val inHour = byHour[h]
            if (inHour.isNullOrEmpty()) list.add(DayRow.Blank(day))
            else inHour.forEach { list.add(DayRow.Item(it, day)) }
        }
        rows = list
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        return when (val row = rows.getOrNull(position)) {
            is DayRow.AllDayHeader -> buildAllDayHeader(ctx, pal, row.day)
            is DayRow.HourHeader -> buildHourHeader(ctx, pal, row.day, row.hour, row.isNow)
            is DayRow.Item -> buildDayItem(ctx, pal, row.entry, row.day)
            is DayRow.Blank -> buildBlank(ctx, row.day)
            else -> RemoteViews(ctx.packageName, R.layout.widget_blank_row)
        }
    }

    override fun getLoadingView(): RemoteViews =
        RemoteViews(ctx.packageName, R.layout.widget_blank_row)

    override fun getViewTypeCount(): Int = 3
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
    override fun onDestroy() {}
}

private fun buildDayItem(ctx: Context, p: Palette, e: DiaryEntry, day: Long): RemoteViews {
    val rv = RemoteViews(ctx.packageName, R.layout.widget_day_item)
    rv.setTextViewText(
        R.id.di_time,
        DateUtil.formatTimeRangeShort(e.dateEpochDay, e.timeMinutes, e.endDay, e.endTimeMinutes)
    )
    val titleText = e.title.ifBlank { "(제목 없음)" } +
        if (e.isMultiDay) "  (${e.dayIndexOf(day)}/${e.dayCount}일차)" else ""
    rv.setTextColor(R.id.di_time, p.accent)
    rv.setTextViewText(R.id.di_title, titleText)
    rv.setTextColor(R.id.di_title, p.textPrimary)
    rv.setTextViewText(R.id.di_mood, e.mood)
    rv.setViewVisibility(R.id.di_mood, if (e.mood.isBlank()) View.GONE else View.VISIBLE)
    rv.setTextViewText(R.id.di_importance_text, "${e.importance}%")
    rv.setTextColor(R.id.di_importance_text, p.textMuted)
    rv.setProgressBar(R.id.di_importance, 100, e.importance, false)

    val tagText = if (e.tags.isEmpty()) "" else e.tags.joinToString(" ") { "#$it" }
    rv.setTextViewText(R.id.di_tags, tagText)
    rv.setTextColor(R.id.di_tags, p.accent)
    rv.setViewVisibility(R.id.di_tags, if (tagText.isBlank()) View.GONE else View.VISIBLE)

    val content = e.content.trim()
    rv.setTextViewText(R.id.di_content, content)
    rv.setTextColor(R.id.di_content, p.textMuted)
    rv.setViewVisibility(R.id.di_content, if (content.isBlank()) View.GONE else View.VISIBLE)

    rv.setOnClickFillInIntent(
        R.id.di_root,
        Intent()
            .putExtra(MainActivity.EXTRA_ENTRY_ID, e.id)
            .putExtra(MainActivity.EXTRA_DATE, day)
    )
    return rv
}
