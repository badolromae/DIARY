package com.jooshin.diary.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jooshin.diary.R
import com.jooshin.diary.data.DiaryEntry
import com.jooshin.diary.ui.MainActivity
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.Prefs

object NotificationHelper {

    private const val CH_BOTH = "diary_both"
    private const val CH_SOUND = "diary_sound"
    private const val CH_VIBRATE = "diary_vibrate"
    private const val CH_SILENT = "diary_silent"

    private const val ID_DAILY = 1

    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pattern = longArrayOf(0, 250, 150, 250)

        fun make(id: String, name: String, importance: Int, withSound: Boolean, withVib: Boolean): NotificationChannel {
            val ch = NotificationChannel(id, name, importance)
            if (withSound) ch.setSound(sound, attrs) else ch.setSound(null, null)
            ch.enableVibration(withVib)
            if (withVib) ch.vibrationPattern = pattern
            ch.setShowBadge(true)
            return ch
        }

        nm.createNotificationChannel(make(CH_BOTH, "일기 알림 (소리+진동)", NotificationManager.IMPORTANCE_HIGH, true, true))
        nm.createNotificationChannel(make(CH_SOUND, "일기 알림 (소리)", NotificationManager.IMPORTANCE_HIGH, true, false))
        nm.createNotificationChannel(make(CH_VIBRATE, "일기 알림 (진동)", NotificationManager.IMPORTANCE_HIGH, false, true))
        nm.createNotificationChannel(make(CH_SILENT, "일기 알림 (무음)", NotificationManager.IMPORTANCE_LOW, false, false))
    }

    private fun channelForStyle(c: Context): String = when (Prefs.notifyStyle(c)) {
        Prefs.STYLE_SOUND -> CH_SOUND
        Prefs.STYLE_VIBRATE -> CH_VIBRATE
        Prefs.STYLE_SILENT -> CH_SILENT
        else -> CH_BOTH
    }

    @SuppressLint("MissingPermission")
    fun notifyDaily(c: Context) {
        val intent = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DATE, DateUtil.today())
            putExtra(MainActivity.EXTRA_NEW, true)
        }
        val pi = PendingIntent.getActivity(
            c, 100, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, channelForStyle(c))
            .setSmallIcon(R.drawable.ic_stat_diary)
            .setColor(ContextCompat.getColor(c, R.color.brand))
            .setContentTitle("오늘의 일기")
            .setContentText("오늘 하루를 기록해 보세요.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        safeNotify(c, ID_DAILY, n)
    }

    @SuppressLint("MissingPermission")
    fun notifyEntry(c: Context, entry: DiaryEntry) {
        val intent = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DATE, entry.dateEpochDay)
            putExtra(MainActivity.EXTRA_ENTRY_ID, entry.id)
        }
        val pi = PendingIntent.getActivity(
            c, (2000 + entry.id).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = entry.title.ifBlank { "일기 알림" }
        val timeStr = DateUtil.formatTime(entry.timeMinutes)
        val body = buildString {
            append(DateUtil.formatShortDate(entry.dateEpochDay))
            if (entry.timeMinutes >= 0) append(" $timeStr")
            if (entry.content.isNotBlank()) append("  ·  ${entry.content.take(40)}")
        }
        val n = NotificationCompat.Builder(c, channelForStyle(c))
            .setSmallIcon(R.drawable.ic_stat_diary)
            .setColor(ContextCompat.getColor(c, R.color.brand))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        safeNotify(c, (3000 + entry.id).toInt(), n)
    }

    private fun safeNotify(c: Context, id: Int, n: android.app.Notification) {
        try {
            NotificationManagerCompat.from(c).notify(id, n)
        } catch (_: SecurityException) {
            // 알림 권한이 없으면 조용히 무시
        }
    }
}
