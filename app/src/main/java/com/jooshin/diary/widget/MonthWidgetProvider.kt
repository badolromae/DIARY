package com.jooshin.diary.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
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
 * - 이번 달 날짜만 표시한다. (지난달/다음달 칸은 비워둠)
 * - 칸마다 직접 PendingIntent 를 붙여서 런처와 무관하게 터치가 동작한다.
 * - 위젯(RemoteViews)에서는 <View> 를 쓸 수 없으므로 한 칸을 TextView 하나로 만들고
 *   날짜/음력/공휴일을 색과 크기가 다른 한 덩이 텍스트로 넣는다.
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
        views.setOnClickPendingIntent(R.id.month_title, WidgetCommon.openDate(c, today))

        // 이번 달 범위
        val firstDate = DateUtil.toDate(anchor)
        val daysInMonth = firstDate.lengthOfMonth()
        val leading = DateUtil.dowIndex(anchor)             // 1일이 무슨 요일인지 (0=일)
        val rowCount = ((leading + daysInMonth) + 6) / 7    // 필요한 줄 수 (4~6)

        val counts = AppDatabase.get(c).diaryDao()
            .getOverlappingSync(anchor, anchor + daysInMonth - 1)
            .countsByDay(anchor, anchor + daysInMonth - 1)

        val cSun = ContextCompat.getColor(c, R.color.widget_day_sun)
        val cSat = ContextCompat.getColor(c, R.color.widget_day_sat)
        val cNormal = ContextCompat.getColor(c, R.color.widget_day_normal)
        val cToday = ContextCompat.getColor(c, R.color.widget_today_text)
        val cLunar = ContextCompat.getColor(c, R.color.widget_lunar_text)
        val cMuted = ContextCompat.getColor(c, R.color.widget_muted_text)
        val cAccent = ContextCompat.getColor(c, R.color.brand_accent)

        views.removeAllViews(R.id.month_rows)
        for (w in 0 until rowCount) {
            val row = RemoteViews(c.packageName, R.layout.widget_month_row)
            for (i in 0 until 7) {
                val cell = RemoteViews(c.packageName, R.layout.widget_month_cell)
                val dayNum = w * 7 + i - leading + 1

                if (dayNum < 1 || dayNum > daysInMonth) {
                    // 지난달/다음달 칸: 완전히 비워 둔다
                    cell.setTextViewText(R.id.cell_day, "")
                    cell.setInt(R.id.cell_day, "setBackgroundResource", 0)
                    row.addView(R.id.row_root, cell)
                    continue
                }

                val ed = anchor + (dayNum - 1)
                val isToday = ed == today
                val dow = DateUtil.dowIndex(ed)
                val info = KoreanHolidays.info(ed)
                val red = dow == 0 || info.isHoliday
                val dayColor = when {
                    isToday -> cToday
                    red -> cSun
                    dow == 6 -> cSat
                    else -> cNormal
                }

                val sb = SpannableStringBuilder()

                // 1행: 날짜
                sb.append(dayNum.toString())
                sb.setSpan(ForegroundColorSpan(dayColor), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isToday) {
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                // 2행: (기록 표시) + 음력
                sb.append("\n")
                val line2Start = sb.length
                if ((counts[ed] ?: 0) > 0) {
                    sb.append("• ")
                    sb.setSpan(
                        ForegroundColorSpan(cAccent), line2Start, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                val lunarStart = sb.length
                sb.append(LunarCalendar.shortLabel(ed))
                sb.setSpan(
                    ForegroundColorSpan(cLunar), lunarStart, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.setSpan(
                    RelativeSizeSpan(0.62f), line2Start, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // 3행: 공휴일/기념일
                val note = info.compact
                if (note.isNotEmpty()) {
                    sb.append("\n")
                    val s3 = sb.length
                    sb.append(note)
                    sb.setSpan(
                        ForegroundColorSpan(if (info.isHoliday) cSun else cMuted),
                        s3, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(RelativeSizeSpan(0.6f), s3, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                cell.setTextViewText(R.id.cell_day, sb)
                cell.setInt(
                    R.id.cell_day, "setBackgroundResource",
                    if (isToday) R.drawable.bg_widget_today_cell else 0
                )
                cell.setOnClickPendingIntent(R.id.cell_day, WidgetCommon.openDate(c, ed))

                row.addView(R.id.row_root, cell)
            }
            views.addView(R.id.month_rows, row)
        }

        mgr.updateAppWidget(id, views)
    }
}
