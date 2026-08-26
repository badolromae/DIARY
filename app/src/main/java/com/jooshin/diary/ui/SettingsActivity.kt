package com.jooshin.diary.ui

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.jooshin.diary.R
import com.jooshin.diary.databinding.ActivitySettingsBinding
import com.jooshin.diary.notify.ReminderScheduler
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.AppTheme
import com.jooshin.diary.util.Prefs
import com.jooshin.diary.widget.WidgetUpdater

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pinSetupLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateSecurityControls()
        }

    private val dailyListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        Prefs.setDailyEnabled(this, checked)
        binding.btnDailyTime.isEnabled = checked
        if (checked) {
            requestNotifIfNeeded()
            ensureExactAlarm()
        }
        ReminderScheduler.scheduleDaily(this)
    }

    private val lockListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        if (checked) {
            if (!Prefs.hasPin(this)) {
                pinSetupLauncher.launch(Intent(this, PinSetupActivity::class.java))
            } else {
                Prefs.setLockEnabled(this, true)
                updateSecurityControls()
            }
        } else {
            Prefs.setLockEnabled(this, false)
            updateSecurityControls()
        }
    }

    private val bioListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        Prefs.setBiometricEnabled(this, checked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.appTheme(this).styleRes)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarSettings.setNavigationOnClickListener { finish() }

        // 디자인(팔레트)
        when (Prefs.appTheme(this)) {
            AppTheme.BLUE -> binding.rbThemeBlue.isChecked = true
            AppTheme.PINK -> binding.rbThemePink.isChecked = true
            AppTheme.MONO -> binding.rbThemeMono.isChecked = true
            else -> binding.rbThemeGreen.isChecked = true
        }
        binding.radioTheme.setOnCheckedChangeListener { _, id ->
            val t = when (id) {
                R.id.rbThemeBlue -> AppTheme.BLUE
                R.id.rbThemePink -> AppTheme.PINK
                R.id.rbThemeMono -> AppTheme.MONO
                else -> AppTheme.GREEN
            }
            if (t != Prefs.appTheme(this)) {
                Prefs.setAppTheme(this, t)
                WidgetUpdater.refreshAll(this)   // 위젯도 같이 바꾼다
                recreate()                        // 설정 화면 즉시 반영
            }
        }

        // 알림
        binding.switchDaily.isChecked = Prefs.isDailyEnabled(this)
        binding.btnDailyTime.isEnabled = Prefs.isDailyEnabled(this)
        updateDailyTimeText()
        binding.switchDaily.setOnCheckedChangeListener(dailyListener)
        binding.btnDailyTime.setOnClickListener { pickDailyTime() }

        when (Prefs.notifyStyle(this)) {
            Prefs.STYLE_SOUND -> binding.rbSound.isChecked = true
            Prefs.STYLE_VIBRATE -> binding.rbVibrate.isChecked = true
            Prefs.STYLE_SILENT -> binding.rbSilent.isChecked = true
            else -> binding.rbBoth.isChecked = true
        }
        binding.radioNotifyStyle.setOnCheckedChangeListener { _, checkedId ->
            val style = when (checkedId) {
                R.id.rbSound -> Prefs.STYLE_SOUND
                R.id.rbVibrate -> Prefs.STYLE_VIBRATE
                R.id.rbSilent -> Prefs.STYLE_SILENT
                else -> Prefs.STYLE_BOTH
            }
            Prefs.setNotifyStyle(this, style)
        }

        // 보안
        binding.btnChangePin.setOnClickListener {
            pinSetupLauncher.launch(Intent(this, PinSetupActivity::class.java))
        }
        updateSecurityControls()

        binding.tvVersion.text = "버전 1.0 · 모든 기록은 이 기기에만 저장됩니다."
    }

    private fun updateDailyTimeText() {
        val minutes = Prefs.dailyHour(this) * 60 + Prefs.dailyMinute(this)
        binding.btnDailyTime.text = "매일 알림 시간: ${DateUtil.formatTime(minutes)}"
    }

    private fun pickDailyTime() {
        TimePickerDialog(
            this,
            { _, h, m ->
                Prefs.setDailyTime(this, h, m)
                updateDailyTimeText()
                ReminderScheduler.scheduleDaily(this)
            },
            Prefs.dailyHour(this), Prefs.dailyMinute(this), false
        ).show()
    }

    private fun updateSecurityControls() {
        val bioAvailable = isBiometricAvailable()
        val lockOn = Prefs.isLockEnabled(this) && Prefs.hasPin(this)

        binding.switchLock.setOnCheckedChangeListener(null)
        binding.switchLock.isChecked = lockOn
        binding.switchLock.setOnCheckedChangeListener(lockListener)

        binding.btnChangePin.isEnabled = lockOn

        binding.switchBiometric.setOnCheckedChangeListener(null)
        binding.switchBiometric.isChecked = Prefs.isBiometricEnabled(this) && bioAvailable
        binding.switchBiometric.isEnabled = lockOn && bioAvailable
        binding.switchBiometric.setOnCheckedChangeListener(bioListener)

        binding.tvBiometricHint.text =
            if (bioAvailable) "지문 또는 얼굴 인식으로도 잠금을 해제합니다."
            else "이 기기에서는 생체인식을 사용할 수 없습니다."
    }

    private fun isBiometricAvailable(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun requestNotifIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("정확한 알림 권한")
                    .setMessage("정해진 시간에 정확히 알림을 울리려면 '알람 및 리마인더' 권한이 필요합니다. 설정으로 이동할까요?")
                    .setNegativeButton("나중에", null)
                    .setPositiveButton("설정 열기") { _, _ ->
                        try {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        } catch (_: Exception) {
                        }
                    }
                    .show()
            }
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
