package com.jooshin.diary.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.jooshin.diary.R

/** 데이터 변경/자정/부팅 시 모든 위젯을 새로고침한다. */
object WidgetUpdater {

    fun refreshAll(c: Context) {
        val mgr = AppWidgetManager.getInstance(c) ?: return
        // 월 위젯은 정적 그리드라 컬렉션 갱신이 필요 없다.
        refresh(c, mgr, MonthWidgetProvider::class.java, 0)
        refresh(c, mgr, WeekWidgetProvider::class.java, R.id.week_list)
        refresh(c, mgr, DayWidgetProvider::class.java, R.id.day_list)
    }

    private fun refresh(c: Context, mgr: AppWidgetManager, cls: Class<*>, collectionViewId: Int) {
        val ids = mgr.getAppWidgetIds(ComponentName(c, cls))
        if (ids.isEmpty()) return
        val i = Intent(c, cls).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        c.sendBroadcast(i)
        if (collectionViewId != 0) mgr.notifyAppWidgetViewDataChanged(ids, collectionViewId)
    }
}
