package com.jooshin.diary.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.jooshin.diary.R
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.KoreanHolidays
import com.jooshin.diary.util.LunarCalendar

class DayWidgetProvider : BaseCalendarWidget() {

    override val collectionViewId = R.id.day_list

    override fun defaultAnchor(): Long = DateUtil.today()

    override fun step(anchor: Long, dir: Int): Long = anchor + dir

    override fun render(c: Context, mgr: AppWidgetManager, id: Int) {
        val anchor = WidgetState.getAnchor(c, id, defaultAnchor())
        val views = RemoteViews(c.packageName, R.layout.widget_day)
        views.setTextViewText(R.id.day_title, DateUtil.formatFullDate(anchor))

        val info = KoreanHolidays.info(anchor)
        val red = DateUtil.dowIndex(anchor) == 0 || info.isHoliday
        views.setTextColor(
            R.id.day_title,
            when {
                red -> ContextCompat.getColor(c, R.color.widget_day_sun)
                DateUtil.dowIndex(anchor) == 6 -> ContextCompat.getColor(c, R.color.widget_day_sat)
                else -> ContextCompat.getColor(c, R.color.widget_header_text)
            }
        )
        val sub = listOf(LunarCalendar.shortLabel(anchor), info.full)
            .filter { it.isNotEmpty() }.joinToString("  ·  ")
        views.setTextViewText(R.id.day_sub, sub)
        views.setTextColor(
            R.id.day_sub,
            if (info.isHoliday) ContextCompat.getColor(c, R.color.widget_day_sun)
            else ContextCompat.getColor(c, R.color.widget_lunar_text)
        )

        views.setOnClickPendingIntent(R.id.btn_prev, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_PREV, id))
        views.setOnClickPendingIntent(R.id.btn_next, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_NEXT, id))
        views.setOnClickPendingIntent(R.id.btn_today, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_TODAY, id))
        views.setOnClickPendingIntent(R.id.btn_add, WidgetCommon.openForDate(c, anchor, true, id))
        views.setOnClickPendingIntent(R.id.day_title, WidgetCommon.openForDate(c, anchor, false, id))

        val svc = Intent(c, DayWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = Uri.parse("diary://widget/day/$id")
        }
        views.setRemoteAdapter(R.id.day_list, svc)
        views.setEmptyView(R.id.day_list, R.id.day_empty)
        views.setPendingIntentTemplate(R.id.day_list, WidgetCommon.clickTemplate(c, "day", id))

        mgr.updateAppWidget(id, views)
        mgr.notifyAppWidgetViewDataChanged(id, R.id.day_list)
    }
}
