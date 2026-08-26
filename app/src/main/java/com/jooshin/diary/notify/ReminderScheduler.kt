package com.jooshin.diary.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jooshin.diary.data.DiaryEntry
import com.jooshin.diary.util.Prefs
import java.util.Calendar

/** AlarmManager 로 매일/개별/자정(위젯 갱신) 알람을 예약한다. */
object ReminderScheduler {
    const val ACTION_DAILY = "com.jooshin.diary.alarm.DAILY"
    const val ACTION_ENTRY = "com.jooshin.diary.alarm.ENTRY"
    const val ACTION_MIDNIGHT = "com.jooshin.diary.alarm.MIDNIGHT"
    const val EXTRA_ENTRY_ID = "entry_id"

    private const val RC_DAILY = 1001
    private const val RC_MIDNIGHT = 1002
    private const val RC_ENTRY_BASE = 100000

    private fun am(c: Context): AlarmManager =
        c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun canExact(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am(c).canScheduleExactAlarms() else true

    private fun pending(c: Context, action: String, rc: Int, entryId: Long? = null): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(action)
        if (entryId != null) i.putExtra(EXTRA_ENTRY_ID, entryId)
        return PendingIntent.getBroadcast(
            c, rc, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun setExact(c: Context, triggerAt: Long, pi: PendingIntent) {
        try {
            if (canExact(c)) {
                am(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun scheduleDaily(c: Context) {
        if (!Prefs.isDailyEnabled(c)) {
            cancelDaily(c)
            return
        }
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, Prefs.dailyHour(c))
            set(Calendar.MINUTE, Prefs.dailyMinute(c))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        setExact(c, cal.timeInMillis, pending(c, ACTION_DAILY, RC_DAILY))
    }

    fun cancelDaily(c: Context) {
        am(c).cancel(pending(c, ACTION_DAILY, RC_DAILY))
    }

    fun scheduleMidnight(c: Context) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }
        setExact(c, cal.timeInMillis, pending(c, ACTION_MIDNIGHT, RC_MIDNIGHT))
    }

    fun scheduleEntry(c: Context, entry: DiaryEntry) {
        if (entry.reminderAtMillis <= 0L) {
            cancelEntry(c, entry.id)
            return
        }
        setExact(
            c, entry.reminderAtMillis,
            pending(c, ACTION_ENTRY, RC_ENTRY_BASE + entry.id.toInt(), entry.id)
        )
    }

    fun cancelEntry(c: Context, entryId: Long) {
        am(c).cancel(pending(c, ACTION_ENTRY, RC_ENTRY_BASE + entryId.toInt(), entryId))
    }
}
