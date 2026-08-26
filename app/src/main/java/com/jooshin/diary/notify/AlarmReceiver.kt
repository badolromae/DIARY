package com.jooshin.diary.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_DAILY -> {
                NotificationHelper.notifyDaily(context)
                ReminderScheduler.scheduleDaily(context) // 다음 날 재예약
            }

            ReminderScheduler.ACTION_MIDNIGHT -> {
                WidgetUpdater.refreshAll(context)
                ReminderScheduler.scheduleMidnight(context)
            }

            ReminderScheduler.ACTION_ENTRY -> {
                val id = intent.getLongExtra(ReminderScheduler.EXTRA_ENTRY_ID, -1L)
                if (id <= 0L) return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val entry = AppDatabase.get(context).diaryDao().getById(id)
                        if (entry != null) NotificationHelper.notifyEntry(context, entry)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
