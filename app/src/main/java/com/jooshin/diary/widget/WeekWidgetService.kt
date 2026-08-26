package com.jooshin.diary.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.jooshin.diary.R
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.data.DiaryEntry
import com.jooshin.diary.data.endDay
import com.jooshin.diary.data.forDay
import com.jooshin.diary.ui.MainActivity
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.KoreanHolidays
import com.jooshin.diary.util.LunarCalendar

class WeekWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WeekFactory(applicationContext, intent)
}

private sealed class WeekRow {
    data class Header(val epochDay: Long, val isToday: Boolean) : WeekRow()
    data class Entry(val entry: DiaryEntry, val day: Long) : WeekRow()
    data class Empty(val epochDay: Long) : WeekRow()
}

private class WeekFactory(
    private val ctx: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private var rows: List<WeekRow> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val weekStart = WidgetState.getAnchor(ctx, appWidgetId, DateUtil.weekStart(DateUtil.today()))
        val today = DateUtil.today()
        val all = AppDatabase.get(ctx).diaryDao().getOverlappingSync(weekStart, weekStart + 6)
        val list = ArrayList<WeekRow>()
        for (i in 0..6) {
            val ed = weekStart + i
            list.add(WeekRow.Header(ed, ed == today))
            val dayItems = all.forDay(ed)
            if (dayItems.isEmpty()) list.add(WeekRow.Empty(ed))
            else dayItems.forEach { list.add(WeekRow.Entry(it, ed)) }
        }
        rows = list
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        return when (val row = rows.getOrNull(position)) {
            is WeekRow.Header -> {
                val rv = RemoteViews(ctx.packageName, R.layout.widget_week_header)
                val d = DateUtil.toDate(row.epochDay)
                val info = KoreanHolidays.info(row.epochDay)
                val label = "${d.dayOfMonth}일 (${DateUtil.weekdayShort(row.epochDay)})" +
                    if (row.isToday) "  · 오늘" else ""
                rv.setTextViewText(R.id.wk_head_day, label)

                val dow = DateUtil.dowIndex(row.epochDay)
                val red = dow == 0 || info.isHoliday
                val color = when {
                    row.isToday -> ContextCompat.getColor(ctx, R.color.brand_accent)
                    red -> ContextCompat.getColor(ctx, R.color.widget_day_sun)
                    dow == 6 -> ContextCompat.getColor(ctx, R.color.widget_day_sat)
                    else -> ContextCompat.getColor(ctx, R.color.widget_header_text)
                }
                rv.setTextColor(R.id.wk_head_day, color)

                val lunar = LunarCalendar.shortLabel(row.epochDay)
                val note = info.short
                val sub = listOf(lunar, note).filter { it.isNotEmpty() }.joinToString("  ·  ")
                rv.setTextViewText(R.id.wk_head_sub, sub)
                rv.setTextColor(
                    R.id.wk_head_sub,
                    if (info.isHoliday) ContextCompat.getColor(ctx, R.color.widget_day_sun)
                    else ContextCompat.getColor(ctx, R.color.widget_lunar_text)
                )

                rv.setOnClickFillInIntent(
                    R.id.wk_header_root,
                    Intent().putExtra(MainActivity.EXTRA_DATE, row.epochDay)
                )
                rv
            }

            is WeekRow.Entry -> {
                val e = row.entry
                val rv = RemoteViews(ctx.packageName, R.layout.widget_week_entry)
                rv.setTextViewText(
                    R.id.wk_time,
                    DateUtil.formatTimeRangeShort(
                        e.dateEpochDay, e.timeMinutes, e.endDay, e.endTimeMinutes
                    )
                )
                rv.setTextViewText(R.id.wk_title, e.title.ifBlank { "(제목 없음)" })
                rv.setTextViewText(R.id.wk_mood, e.mood)
                rv.setViewVisibility(R.id.wk_mood, if (e.mood.isBlank()) View.GONE else View.VISIBLE)
                rv.setProgressBar(R.id.wk_importance, 100, e.importance, false)
                val fill = Intent()
                    .putExtra(MainActivity.EXTRA_ENTRY_ID, e.id)
                    .putExtra(MainActivity.EXTRA_DATE, row.day)
                rv.setOnClickFillInIntent(R.id.wk_entry_root, fill)
                rv
            }

            is WeekRow.Empty -> {
                val rv = RemoteViews(ctx.packageName, R.layout.widget_week_empty)
                val fill = Intent()
                    .putExtra(MainActivity.EXTRA_DATE, row.epochDay)
                    .putExtra(MainActivity.EXTRA_NEW, true)
                rv.setOnClickFillInIntent(R.id.wk_empty_root, fill)
                rv
            }

            else -> RemoteViews(ctx.packageName, R.layout.widget_week_empty)
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 3
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
    override fun onDestroy() {}
}
