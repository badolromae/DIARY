package com.jooshin.diary.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.jooshin.diary.R
import com.jooshin.diary.databinding.ActivityLockBinding
import com.jooshin.diary.util.AppLock
import com.jooshin.diary.util.Prefs

class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private val input = StringBuilder()
    private var pinLen = 4
    private val dots = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.appTheme(this).styleRes)
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pinLen = Prefs.pinLength(this)
        buildDots()

        val digitButtons = listOf(
            binding.key0, binding.key1, binding.key2, binding.key3, binding.key4,
            binding.key5, binding.key6, binding.key7, binding.key8, binding.key9
        )
        digitButtons.forEachIndexed { digit, btn ->
            btn.setOnClickListener { appendDigit(digit) }
        }
        binding.keyDel.setOnClickListener { deleteDigit() }
        binding.btnBiometric.setOnClickListener { showBiometric() }

        binding.btnBiometric.visibility =
            if (Prefs.isBiometricEnabled(this) && isBiometricAvailable()) View.VISIBLE else View.GONE

        // 뒤로가기 시 앱 종료(잠금 우회 방지)
        onBackPressedDispatcher.addCallback(this) { finishAffinity() }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.isBiometricEnabled(this) && isBiometricAvailable()) showBiometric()
    }

    private fun buildDots() {
        binding.dotsContainer.removeAllViews()
        dots.clear()
        val size = (14 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()
        for (i in 0 until pinLen) {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginStart = margin; lp.marginEnd = margin
            v.layoutParams = lp
            v.setBackgroundResource(R.drawable.dot_pin_empty)
            dots.add(v)
            binding.dotsContainer.addView(v)
        }
    }

    private fun updateDots() {
        dots.forEachIndexed { i, v ->
            v.setBackgroundResource(
                if (i < input.length) R.drawable.dot_pin_filled else R.drawable.dot_pin_empty
            )
        }
    }

    private fun appendDigit(d: Int) {
        if (input.length >= pinLen) return
        binding.tvLockError.visibility = View.INVISIBLE
        input.append(d)
        updateDots()
        if (input.length == pinLen) verify()
    }

    private fun deleteDigit() {
        if (input.isNotEmpty()) {
            input.deleteCharAt(input.length - 1)
            updateDots()
        }
    }

    private fun verify() {
        if (Prefs.verifyPin(this, input.toString())) {
            unlock()
        } else {
            binding.tvLockError.visibility = View.VISIBLE
            input.setLength(0)
            updateDots()
        }
    }

    private fun unlock() {
        AppLock.markUnlocked()
        finish()
    }

    private fun isBiometricAvailable(): Boolean =
        BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometric() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("잠금 해제")
            .setSubtitle("지문 또는 얼굴로 인증하세요")
            .setNegativeButtonText("PIN 사용")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Exception) {
        }
    }
}
