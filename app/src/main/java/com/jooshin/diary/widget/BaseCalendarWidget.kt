package com.jooshin.diary.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/** 월/주/일 위젯의 공통 동작(이전·다음·오늘 이동, 갱신). */
abstract class BaseCalendarWidget : AppWidgetProvider() {

    /** RemoteAdapter 가 연결된 컬렉션 뷰 id (없으면 0) */
    protected open val collectionViewId: Int = 0

    /** 기준 날짜 기본값 (오늘 기준) */
    protected abstract fun defaultAnchor(): Long

    /** dir = -1(이전) / +1(다음) 이동 후 새 anchor */
    protected abstract fun step(anchor: Long, dir: Int): Long

    /** 위젯 한 개 렌더. DB 를 읽을 수 있으므로 백그라운드 스레드에서 호출된다. */
    protected abstract fun render(c: Context, mgr: AppWidgetManager, id: Int)

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        runOffMainThread {
            appWidgetIds.forEach { safeRender(context, mgr, it) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == WidgetCommon.ACTION_PREV ||
            action == WidgetCommon.ACTION_NEXT ||
            action == WidgetCommon.ACTION_TODAY
        ) {
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val newAnchor = when (action) {
                WidgetCommon.ACTION_PREV -> step(WidgetState.getAnchor(context, id, defaultAnchor()), -1)
                WidgetCommon.ACTION_NEXT -> step(WidgetState.getAnchor(context, id, defaultAnchor()), +1)
                else -> defaultAnchor()
            }
            WidgetState.setAnchor(context, id, newAnchor)
            runOffMainThread {
                val mgr = AppWidgetManager.getInstance(context) ?: return@runOffMainThread
                safeRender(context, mgr, id)
                if (collectionViewId != 0) mgr.notifyAppWidgetViewDataChanged(id, collectionViewId)
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetState.clear(context, it) }
    }

    private fun safeRender(c: Context, mgr: AppWidgetManager, id: Int) {
        try {
            render(c, mgr, id)
        } catch (t: Throwable) {
            // 위젯 갱신 실패로 앱이 죽지 않도록 무시
        }
    }

    /**
     * 브로드캐스트 처리 시간을 연장(goAsync)한 뒤 백그라운드 스레드에서 실행.
     * goAsync 를 쓸 수 없는 상황이면 그냥 스레드만 띄운다.
     */
    private fun runOffMainThread(block: () -> Unit) {
        val pending = try {
            goAsync()
        } catch (t: Throwable) {
            null
        }
        Thread {
            try {
                block()
            } catch (t: Throwable) {
                // 무시
            } finally {
                try {
                    pending?.finish()
                } catch (t: Throwable) {
                    // 무시
                }
            }
        }.start()
    }
}
