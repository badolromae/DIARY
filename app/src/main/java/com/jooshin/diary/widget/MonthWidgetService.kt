package com.jooshin.diary.widget

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.jooshin.diary.R

/**
 * (사용하지 않음)
 *
 * 월 위젯은 컬렉션(GridView) 방식에서 정적 그리드 방식으로 바뀌었습니다.
 * 일부 런처에서 GridView 항목 터치가 먹지 않는 문제 때문입니다.
 * AndroidManifest 의 선언과 호환을 위해 빈 서비스로 남겨둡니다.
 */
class MonthWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = EmptyFactory(this)

    private class EmptyFactory(private val ctx: android.content.Context) : RemoteViewsFactory {
        override fun onCreate() {}
        override fun onDataSetChanged() {}
        override fun getCount(): Int = 0
        override fun getViewAt(position: Int): RemoteViews =
            RemoteViews(ctx.packageName, R.layout.widget_month_cell)

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = true
        override fun onDestroy() {}
    }
}
