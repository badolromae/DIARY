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
import com.jooshin.diary.data.endDay
import com.jooshin.diary.data.forDay
import com.jooshin.diary.ui.MainActivity
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.KoreanHolidays
import com.jooshin.diary.util.LunarCalendar
import com.jooshin.diary.util.Palette

class WeekWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WeekFactory(applicationContext, intent)
}

private sealed class WeekRow {
    data class Header(val epochDay: Long, val isToday: Boolean) : WeekRow()
    data class Entry(val entry: DiaryEntry, val day: Long) : WeekRow()
    data class Blank(val epochDay: Long) : WeekRow()
}

private class WeekFactory(
    private val ctx: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private var rows: List<WeekRow> = emptyList()
    private var pal: Palette = Palette.of(ctx)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        pal = Palette.of(ctx)
        val weekStart = WidgetState.getAnchor(ctx, appWidgetId, DateUtil.weekStart(DateUtil.today()))
        val today = DateUtil.today()
        val all = AppDatabase.get(ctx).diaryDao().getOverlappingSync(weekStart, weekStart + 6)
        val list = ArrayList<WeekRow>()
        for (i in 0..6) {
            val ed = weekStart + i
            list.add(WeekRow.Header(ed, ed == today))
            val dayItems = all.forDay(ed)
            if (dayItems.isEmpty()) list.add(WeekRow.Blank(ed))
            else dayItems.forEach { list.add(WeekRow.Entry(it, ed)) }
        }
        rows = list
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        return when (val row = rows.getOrNull(position)) {
            is WeekRow.Header -> buildDayHeader(ctx, pal, row.epochDay, row.isToday)
            is WeekRow.Entry -> buildEntryRow(ctx, pal, row.entry, row.day)
            is WeekRow.Blank -> buildBlank(ctx, row.epochDay)
            else -> RemoteViews(ctx.packageName, R.layout.widget_blank_row)
        }
    }

    // 로딩 중에 기본 "로드 중.." 문구가 뜨지 않도록 빈 줄을 돌려준다.
    override fun getLoadingView(): RemoteViews =
        RemoteViews(ctx.packageName, R.layout.widget_blank_row)

    override fun getViewTypeCount(): Int = 3
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
    override fun onDestroy() {}
}

/** "8/24 월요일" 헤더 + 오른쪽에 음력·공휴일 */
internal fun buildDayHeader(ctx: Context, p: Palette, epochDay: Long, isToday: Boolean): RemoteViews {
    val rv = RemoteViews(ctx.packageName, R.layout.widget_week_header)
    rv.setInt(R.id.wk_header_root, "setBackgroundResource", p.widgetSectionRes)
    val info = KoreanHolidays.info(epochDay)
    val label = DateUtil.formatDateWeekdayFull(epochDay) + if (isToday) "  · 오늘" else ""
    rv.setTextViewText(R.id.wk_head_day, label)

    val dow = DateUtil.dowIndex(epochDay)
    val red = dow == 0 || info.isHoliday
    rv.setTextColor(
        R.id.wk_head_day,
        when {
            isToday -> p.accent
            red -> p.sun
            dow == 6 -> p.sat
            else -> p.textPrimary
        }
    )

    val sub = listOf(LunarCalendar.shortLabel(epochDay), info.short)
        .filter { it.isNotEmpty() }.joinToString("  ·  ")
    rv.setTextViewText(R.id.wk_head_sub, sub)
    rv.setTextColor(R.id.wk_head_sub, if (info.isHoliday) p.sun else p.calLunar)

    rv.setOnClickFillInIntent(
        R.id.wk_header_root,
        Intent().putExtra(MainActivity.EXTRA_DATE, epochDay)
    )
    return rv
}

/** 시간대 헤더("오전 9시") — 일 위젯에서 사용 */
internal fun buildHourHeader(
    ctx: Context, p: Palette, epochDay: Long, hour: Int, isNow: Boolean
): RemoteViews {
    val rv = RemoteViews(ctx.packageName, R.layout.widget_week_header)
    rv.setInt(R.id.wk_header_root, "setBackgroundResource", p.widgetSectionRes)
    rv.setTextViewText(R.id.wk_head_day, DateUtil.formatHour(hour))
    rv.setTextColor(R.id.wk_head_day, if (isNow) p.accent else p.textPrimary)
    rv.setTextViewText(R.id.wk_head_sub, if (isNow) "지금" else "")
    rv.setTextColor(R.id.wk_head_sub, p.accent)
    rv.setOnClickFillInIntent(
        R.id.wk_header_root,
        Intent()
            .putExtra(MainActivity.EXTRA_DATE, epochDay)
            .putExtra(MainActivity.EXTRA_NEW, true)
            .putExtra(MainActivity.EXTRA_TIME, hour * 60)
    )
    return rv
}

/** 종일/이어지는 일정 구간 헤더 */
internal fun buildAllDayHeader(ctx: Context, p: Palette, epochDay: Long): RemoteViews {
    val rv = RemoteViews(ctx.packageName, R.layout.widget_week_header)
    rv.setInt(R.id.wk_header_root, "setBackgroundResource", p.widgetSectionRes)
    rv.setTextViewText(R.id.wk_head_day, "종일")
    rv.setTextColor(R.id.wk_head_day, p.textPrimary)
    rv.setTextViewText(R.id.wk_head_sub, "")
    rv.setOnClickFillInIntent(
        R.id.wk_header_root,
        Intent().putExtra(MainActivity.EXTRA_DATE, epochDay)
    )
    return rv
}

internal fun buildEntryRow(ctx: Context, p: Palette, e: DiaryEntry, day: Long): RemoteViews {
    val rv = RemoteViews(ctx.packageName, R.layout.widget_week_entry)
    rv.setTextViewText(
        R.id.wk_time,
        DateUtil.formatTimeRangeShort(e.dateEpochDay, e.timeMinutes, e.endDay, e.endTimeMinutes)
    )
    rv.setTextColor(R.id.wk_time, p.accent)
    rv.setTextViewText(R.id.wk_title, e.title.ifBlank { "(제목 없음)" })
    rv.setTextColor(R.id.wk_title, p.textPrimary)
    rv.setTextViewText(R.id.wk_mood, e.mood)
    rv.setViewVisibility(R.id.wk_mood, if (e.mood.isBlank()) View.GONE else View.VISIBLE)
    rv.setProgressBar(R.id.wk_importance, 100, e.importance, false)
    rv.setOnClickFillInIntent(
        R.id.wk_entry_root,
        Intent()
            .putExtra(MainActivity.EXTRA_ENTRY_ID, e.id)
            .putExtra(MainActivity.EXTRA_DATE, day)
    )
    return rv
}

internal fun buildBlank(ctx: Context, epochDay: Long): RemoteViews {
    val rv = RemoteViews(ctx.packageName, R.layout.widget_blank_row)
    rv.setOnClickFillInIntent(
        R.id.blank_root,
        Intent().putExtra(MainActivity.EXTRA_DATE, epochDay)
    )
    return rv
}
