package com.jooshin.diary.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.jooshin.diary.R
import com.jooshin.diary.util.DateUtil

class WeekWidgetProvider : BaseCalendarWidget() {

    override val collectionViewId = R.id.week_list

    override fun defaultAnchor(): Long = DateUtil.weekStart(DateUtil.today())

    override fun step(anchor: Long, dir: Int): Long = anchor + dir * 7L

    override fun render(c: Context, mgr: AppWidgetManager, id: Int) {
        val anchor = WidgetState.getAnchor(c, id, defaultAnchor())
        val views = RemoteViews(c.packageName, R.layout.widget_week)
        val title = "${DateUtil.formatShortDate(anchor)} ~ ${DateUtil.formatShortDate(anchor + 6)}"
        views.setTextViewText(R.id.week_title, title)

        views.setOnClickPendingIntent(R.id.btn_prev, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_PREV, id))
        views.setOnClickPendingIntent(R.id.btn_next, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_NEXT, id))
        views.setOnClickPendingIntent(R.id.btn_today, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_TODAY, id))
        views.setOnClickPendingIntent(R.id.week_title, WidgetCommon.openForDate(c, DateUtil.today(), false, id))

        val svc = Intent(c, WeekWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = Uri.parse("diary://widget/week/$id")
        }
        views.setRemoteAdapter(R.id.week_list, svc)
        views.setPendingIntentTemplate(R.id.week_list, WidgetCommon.clickTemplate(c, "week", id))

        mgr.updateAppWidget(id, views)
        mgr.notifyAppWidgetViewDataChanged(id, R.id.week_list)
    }
}
