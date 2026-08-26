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

class DayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        DayFactory(applicationContext, intent)
}

private class DayFactory(
    private val ctx: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private var items: List<DiaryEntry> = emptyList()
    private var day: Long = DateUtil.today()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val anchor = WidgetState.getAnchor(ctx, appWidgetId, DateUtil.today())
        day = anchor
        items = AppDatabase.get(ctx).diaryDao().getForDaySync(anchor)
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val e = items.getOrNull(position)
            ?: return RemoteViews(ctx.packageName, R.layout.widget_day_item)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_day_item)
        rv.setTextViewText(
            R.id.di_time,
            DateUtil.formatTimeRangeShort(e.dateEpochDay, e.timeMinutes, e.endDay, e.endTimeMinutes)
        )
        val titleText = e.title.ifBlank { "(제목 없음)" } +
            if (e.isMultiDay) "  (${e.dayIndexOf(day)}/${e.dayCount}일차)" else ""
        rv.setTextViewText(R.id.di_title, titleText)
        rv.setTextViewText(R.id.di_mood, e.mood)
        rv.setViewVisibility(R.id.di_mood, if (e.mood.isBlank()) View.GONE else View.VISIBLE)
        rv.setTextViewText(R.id.di_importance_text, "${e.importance}%")
        rv.setProgressBar(R.id.di_importance, 100, e.importance, false)

        val tagText = if (e.tags.isEmpty()) "" else e.tags.joinToString(" ") { "#$it" }
        rv.setTextViewText(R.id.di_tags, tagText)
        rv.setViewVisibility(R.id.di_tags, if (tagText.isBlank()) View.GONE else View.VISIBLE)

        val content = e.content.trim()
        rv.setTextViewText(R.id.di_content, content)
        rv.setViewVisibility(R.id.di_content, if (content.isBlank()) View.GONE else View.VISIBLE)

        val fill = Intent()
            .putExtra(MainActivity.EXTRA_ENTRY_ID, e.id)
            .putExtra(MainActivity.EXTRA_DATE, day)
        rv.setOnClickFillInIntent(R.id.di_root, fill)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() {}
}
