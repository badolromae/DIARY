package com.jooshin.diary

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jooshin.diary.notify.NotificationHelper
import com.jooshin.diary.util.AppLock

class DiaryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)

        // 앱이 백그라운드로 나가면 잠금 상태로 전환(잠금 사용 중일 때)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                AppLock.onEnterBackground()
            }
        })
    }
}
