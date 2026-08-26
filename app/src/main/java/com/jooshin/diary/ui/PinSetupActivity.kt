package com.jooshin.diary.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.jooshin.diary.databinding.ActivityPinBinding
import com.jooshin.diary.util.Prefs

class PinSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.appTheme(this).styleRes)
        super.onCreate(savedInstanceState)
        binding = ActivityPinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarPin)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarPin.setNavigationOnClickListener { finish() }

        binding.btnSavePin.setOnClickListener { savePin() }
    }

    private fun savePin() {
        val pin = binding.etPin.text?.toString()?.trim() ?: ""
        val confirm = binding.etPinConfirm.text?.toString()?.trim() ?: ""
        when {
            pin.length < 4 || pin.length > 6 || !pin.all { it.isDigit() } -> {
                binding.tilPin.error = "PIN은 숫자 4~6자리여야 합니다."
            }
            pin != confirm -> {
                binding.tilPin.error = null
                binding.tilPinConfirm.error = "PIN이 일치하지 않습니다."
            }
            else -> {
                Prefs.setPin(this, pin)
                Prefs.setLockEnabled(this, true)
                android.widget.Toast.makeText(this, "PIN이 설정되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
