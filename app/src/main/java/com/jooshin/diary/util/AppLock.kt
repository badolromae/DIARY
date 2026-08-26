package com.jooshin.diary.util

import android.content.Context
import android.os.SystemClock

/**
 * 인메모리 잠금 상태 관리.
 * - 프로세스가 새로 뜨면 잠금 상태로 시작한다(unlocked=false).
 * - 앱이 백그라운드로 나간 뒤 GRACE_MS 이상 지나면 다시 잠근다.
 *   (사진 선택/권한 요청 등으로 잠깐 백그라운드로 갔다 오는 경우엔 다시 묻지 않음)
 */
object AppLock {
    @Volatile private var unlocked = false
    @Volatile private var backgroundedAt = 0L
    private const val GRACE_MS = 20_000L

    fun onEnterBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun markUnlocked() {
        unlocked = true
        backgroundedAt = 0L
    }

    fun isLockRequired(c: Context): Boolean {
        if (!Prefs.isLockEnabled(c) || !Prefs.hasPin(c)) return false
        if (!unlocked) return true
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > GRACE_MS) {
            unlocked = false
            return true
        }
        return false
    }
}
