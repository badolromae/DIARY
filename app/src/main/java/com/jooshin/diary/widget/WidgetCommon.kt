package com.jooshin.diary.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jooshin.diary.ui.MainActivity

object WidgetCommon {
    const val ACTION_PREV = "com.jooshin.diary.action.WIDGET_PREV"
    const val ACTION_NEXT = "com.jooshin.diary.action.WIDGET_NEXT"
    const val ACTION_TODAY = "com.jooshin.diary.action.WIDGET_TODAY"
    const val ACTION_REFRESH = "com.jooshin.diary.action.WIDGET_REFRESH"

    /** 이전/다음/오늘 버튼용 (해당 위젯 Provider 로 브로드캐스트) */
    fun navPendingIntent(c: Context, cls: Class<*>, action: String, id: Int): PendingIntent {
        val i = Intent(c, cls).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = Uri.parse("diary://nav/$action/$id")
        }
        return PendingIntent.getBroadcast(
            c, 0, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** 컬렉션(그리드/리스트) 항목 클릭 템플릿. fill-in 을 받아야 하므로 MUTABLE. */
    fun clickTemplate(c: Context, tag: String, id: Int): PendingIntent {
        val i = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("diary://open/$tag/$id")
        }
        return PendingIntent.getActivity(
            c, 0, i,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * 월 달력 칸(날짜) 클릭용. 날짜만으로 구분하므로 위젯이 여러 개여도 재사용된다.
     * 컬렉션(fill-in) 방식이 아니라 칸마다 직접 붙이는 PendingIntent 이므로
     * 런처 종류와 상관없이 터치가 동작한다.
     */
    fun openDate(c: Context, epochDay: Long): PendingIntent {
        val i = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DATE, epochDay)
            data = Uri.parse("diary://day/$epochDay")
        }
        return PendingIntent.getActivity(
            c, 0, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** 헤더의 '추가/제목' 등 고정 클릭용 */
    fun openForDate(c: Context, epochDay: Long, newEntry: Boolean, id: Int): PendingIntent {
        val i = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DATE, epochDay)
            if (newEntry) putExtra(MainActivity.EXTRA_NEW, true)
            data = Uri.parse("diary://date/$id/$epochDay/$newEntry")
        }
        return PendingIntent.getActivity(
            c, 0, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
