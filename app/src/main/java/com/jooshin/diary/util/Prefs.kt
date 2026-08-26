package com.jooshin.diary.util

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** 앱 설정 저장소 (SharedPreferences). 모든 값은 기기 내부에만 저장된다. */
object Prefs {
    private const val FILE = "diary_prefs"

    // 알림 방식
    const val STYLE_BOTH = 0
    const val STYLE_SOUND = 1
    const val STYLE_VIBRATE = 2
    const val STYLE_SILENT = 3

    private const val K_LOCK = "lock_enabled"
    private const val K_PIN_HASH = "pin_hash"
    private const val K_PIN_SALT = "pin_salt"
    private const val K_PIN_LEN = "pin_len"
    private const val K_BIO = "biometric_enabled"
    private const val K_DAILY = "daily_enabled"
    private const val K_DAILY_H = "daily_hour"
    private const val K_DAILY_M = "daily_minute"
    private const val K_STYLE = "notify_style"
    private const val K_FIRST_RUN = "first_run_done"
    private const val K_THEME = "app_theme"

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- 디자인(팔레트) ----
    fun themeKey(c: Context): String = p(c).getString(K_THEME, AppTheme.GREEN.key) ?: AppTheme.GREEN.key
    fun appTheme(c: Context): AppTheme = AppTheme.of(themeKey(c))
    fun setAppTheme(c: Context, t: AppTheme) = p(c).edit().putString(K_THEME, t.key).apply()

    // ---- 잠금 ----
    fun isLockEnabled(c: Context) = p(c).getBoolean(K_LOCK, false)
    fun setLockEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(K_LOCK, v).apply()

    fun isBiometricEnabled(c: Context) = p(c).getBoolean(K_BIO, true)
    fun setBiometricEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(K_BIO, v).apply()

    fun hasPin(c: Context) = p(c).getString(K_PIN_HASH, null) != null

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.toHex()
        val hash = hash(pin, saltHex)
        p(c).edit()
            .putString(K_PIN_SALT, saltHex)
            .putString(K_PIN_HASH, hash)
            .putInt(K_PIN_LEN, pin.length)
            .apply()
    }

    fun pinLength(c: Context): Int = p(c).getInt(K_PIN_LEN, 4)

    fun verifyPin(c: Context, pin: String): Boolean {
        val salt = p(c).getString(K_PIN_SALT, null) ?: return false
        val stored = p(c).getString(K_PIN_HASH, null) ?: return false
        return hash(pin, salt) == stored
    }

    fun clearPin(c: Context) {
        p(c).edit().remove(K_PIN_HASH).remove(K_PIN_SALT).remove(K_PIN_LEN).apply()
    }

    private fun hash(pin: String, saltHex: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(saltHex.toByteArray())
        return md.digest(pin.toByteArray()).toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    // ---- 알림 ----
    fun isDailyEnabled(c: Context) = p(c).getBoolean(K_DAILY, false)
    fun setDailyEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(K_DAILY, v).apply()

    fun dailyHour(c: Context) = p(c).getInt(K_DAILY_H, 22)
    fun dailyMinute(c: Context) = p(c).getInt(K_DAILY_M, 0)
    fun setDailyTime(c: Context, h: Int, m: Int) =
        p(c).edit().putInt(K_DAILY_H, h).putInt(K_DAILY_M, m).apply()

    fun notifyStyle(c: Context) = p(c).getInt(K_STYLE, STYLE_BOTH)
    fun setNotifyStyle(c: Context, v: Int) = p(c).edit().putInt(K_STYLE, v).apply()

    // ---- 최초 실행 ----
    fun isFirstRunDone(c: Context) = p(c).getBoolean(K_FIRST_RUN, false)
    fun setFirstRunDone(c: Context) = p(c).edit().putBoolean(K_FIRST_RUN, true).apply()
}
