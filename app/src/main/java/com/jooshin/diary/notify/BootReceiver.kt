package com.jooshin.diary.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 부팅/시간대 변경/앱 업데이트 후 알람을 다시 등록하고 위젯을 갱신한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.scheduleDaily(context)
                ReminderScheduler.scheduleMidnight(context)
                val now = System.currentTimeMillis()
                AppDatabase.get(context).diaryDao().getWithReminders().forEach { e ->
                    if (e.reminderAtMillis > now) ReminderScheduler.scheduleEntry(context, e)
                }
                WidgetUpdater.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
