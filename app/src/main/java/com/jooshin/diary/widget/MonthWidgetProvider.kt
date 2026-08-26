package com.jooshin.diary.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.jooshin.diary.R
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.data.countsByDay
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.KoreanHolidays
import com.jooshin.diary.util.LunarCalendar

/**
 * 월 달력 위젯.
 *
 * GridView + RemoteViewsService(컬렉션) 방식은 런처에 따라 항목 터치가
 * 동작하지 않는 경우가 있어, 6주 x 7일 셀을 직접 만들고 칸마다
 * PendingIntent 를 붙이는 정적 그리드 방식으로 구현했다.
 */
class MonthWidgetProvider : BaseCalendarWidget() {

    override fun defaultAnchor(): Long = DateUtil.firstOfMonthOf(DateUtil.today())

    override fun step(anchor: Long, dir: Int): Long = DateUtil.addMonths(anchor, dir)

    override fun render(c: Context, mgr: AppWidgetManager, id: Int) {
        val anchor = WidgetState.getAnchor(c, id, defaultAnchor())
        val views = RemoteViews(c.packageName, R.layout.widget_month)

        views.setTextViewText(R.id.month_title, DateUtil.formatMonthTitle(anchor))
        val today = DateUtil.today()
        val todayLunar = LunarCalendar.shortLabel(today)
        views.setTextViewText(
            R.id.month_sub,
            if (todayLunar.isEmpty()) "오늘 ${DateUtil.formatShortDate(today)}"
            else "오늘 ${DateUtil.formatShortDate(today)} ($todayLunar)"
        )

        views.setOnClickPendingIntent(
            R.id.btn_prev, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_PREV, id)
        )
        views.setOnClickPendingIntent(
            R.id.btn_next, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_NEXT, id)
        )
        views.setOnClickPendingIntent(
            R.id.btn_today, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_TODAY, id)
        )
        views.setOnClickPendingIntent(
            R.id.month_title, WidgetCommon.openForDate(c, today, false, id)
        )

        val gridStart = DateUtil.monthGridStart(anchor)
        val gridEnd = gridStart + 41
        val monthValue = DateUtil.toDate(anchor).monthValue
        val counts = AppDatabase.get(c).diaryDao()
            .getOverlappingSync(gridStart, gridEnd)
            .countsByDay(gridStart, gridEnd)

        val cToday = ContextCompat.getColor(c, R.color.widget_today_text)
        val cOutside = ContextCompat.getColor(c, R.color.widget_day_outside)
        val cSun = ContextCompat.getColor(c, R.color.widget_day_sun)
        val cSat = ContextCompat.getColor(c, R.color.widget_day_sat)
        val cNormal = ContextCompat.getColor(c, R.color.widget_day_normal)
        val cLunar = ContextCompat.getColor(c, R.color.widget_lunar_text)
        val cMuted = ContextCompat.getColor(c, R.color.widget_muted_text)

        views.removeAllViews(R.id.month_rows)
        for (w in 0 until 6) {
            val row = RemoteViews(c.packageName, R.layout.widget_month_row)
            for (i in 0 until 7) {
                val ed = gridStart + w * 7 + i
                val d = DateUtil.toDate(ed)
                val inMonth = d.monthValue == monthValue
                val isToday = ed == today
                val dow = DateUtil.dowIndex(ed)
                val info = KoreanHolidays.info(ed)
                val red = dow == 0 || info.isHoliday

                val cell = RemoteViews(c.packageName, R.layout.widget_month_cell)
                cell.setTextViewText(R.id.cell_day, d.dayOfMonth.toString())
                cell.setTextColor(
                    R.id.cell_day,
                    when {
                        isToday -> cToday
                        !inMonth -> cOutside
                        red -> cSun
                        dow == 6 -> cSat
                        else -> cNormal
                    }
                )
                cell.setInt(
                    R.id.cell_day, "setBackgroundResource",
                    if (isToday) R.drawable.bg_widget_today else 0
                )

                cell.setTextViewText(R.id.cell_lunar, LunarCalendar.shortLabel(ed))
                cell.setTextColor(R.id.cell_lunar, if (inMonth) cLunar else cOutside)

                val note = info.short
                if (note.isEmpty()) {
                    cell.setViewVisibility(R.id.cell_note, View.GONE)
                } else {
                    cell.setViewVisibility(R.id.cell_note, View.VISIBLE)
                    cell.setTextViewText(R.id.cell_note, note)
                    cell.setTextColor(
                        R.id.cell_note,
                        when {
                            !inMonth -> cOutside
                            info.isHoliday -> cSun
                            else -> cMuted
                        }
                    )
                }

                cell.setViewVisibility(
                    R.id.cell_dot,
                    if ((counts[ed] ?: 0) > 0) View.VISIBLE else View.INVISIBLE
                )

                // 칸마다 직접 PendingIntent → 런처와 무관하게 터치가 동작한다.
                cell.setOnClickPendingIntent(R.id.cell_root, WidgetCommon.openDate(c, ed))

                row.addView(R.id.row_root, cell)
            }
            views.addView(R.id.month_rows, row)
        }

        mgr.updateAppWidget(id, views)
    }
}
