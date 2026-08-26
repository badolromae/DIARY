package com.jooshin.diary.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.jooshin.diary.R
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.data.DiaryEntry
import com.jooshin.diary.databinding.ActivityEditorBinding
import com.jooshin.diary.notify.ReminderScheduler
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.ImageStore
import com.jooshin.diary.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

class EntryEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private val dao by lazy { AppDatabase.get(this).diaryDao() }

    private var entryId: Long = 0L
    private var dateEpochDay: Long = DateUtil.today()
    private var timeMinutes: Int = -1
    private var endDateEpochDay: Long = DateUtil.today()
    private var endTimeMinutes: Int = -1
    private var mood: String = ""
    private var reminderAtMillis: Long = 0L
    private var createdAt: Long = 0L
    private var suppressAllDayCallback = false

    private val photos = mutableListOf<String>()
    private val originalPhotos = mutableListOf<String>()
    private lateinit var photoAdapter: PhotoThumbAdapter
    private var saved = false

    private val moodViews = mutableListOf<TextView>()
    private val moodEmojis = listOf("😊", "🙂", "😐", "😔", "😢", "😡", "😴", "🤩", "🥳", "😍", "😎", "🤔")

    private val pickImages =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
            if (uris.isNotEmpty()) importPhotos(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarEditor)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarEditor.setNavigationOnClickListener { finish() }

        entryId = intent.getLongExtra(MainActivity.EXTRA_ENTRY_ID, 0L)
        val incomingDate = intent.getLongExtra(MainActivity.EXTRA_DATE, Long.MIN_VALUE)
        if (incomingDate != Long.MIN_VALUE) dateEpochDay = incomingDate
        endDateEpochDay = dateEpochDay

        binding.toolbarEditor.title = if (entryId > 0L) "일기 수정" else "새 일기"

        buildMoodPicker()

        binding.sliderImportance.addOnChangeListener { _, value, _ ->
            binding.tvImportance.text = "중요도 ${value.toInt()}%"
        }
        binding.sliderImportance.value = 50f
        binding.tvImportance.text = "중요도 50%"

        binding.etTags.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                renderTagChips(parseTags(s?.toString() ?: ""))
            }
        })

        photoAdapter = PhotoThumbAdapter(photos) { pos -> removePhoto(pos) }
        binding.recyclerPhotos.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerPhotos.adapter = photoAdapter

        binding.btnAddPhoto.setOnClickListener {
            pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnDate.setOnClickListener { pickStartDate() }
        binding.btnTime.setOnClickListener { pickStartTime() }
        binding.btnEndDate.setOnClickListener { pickEndDate() }
        binding.btnEndTime.setOnClickListener { pickEndTime() }
        binding.switchAllDay.setOnCheckedChangeListener { _, checked ->
            if (suppressAllDayCallback) return@setOnCheckedChangeListener
            if (checked) {
                timeMinutes = -1
                endTimeMinutes = -1
            } else {
                if (timeMinutes < 0) timeMinutes = 9 * 60
                if (endTimeMinutes < 0) endTimeMinutes = (timeMinutes + 60).coerceAtMost(23 * 60 + 59)
            }
            updateDateTimeButtons()
        }

        binding.switchReminder.setOnCheckedChangeListener { _, checked ->
            binding.btnReminderTime.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) {
                if (reminderAtMillis <= 0L) reminderAtMillis = defaultReminderMillis()
                updateReminderButton()
            } else {
                reminderAtMillis = 0L
            }
        }
        binding.btnReminderTime.setOnClickListener { pickReminder() }

        binding.btnSave.setOnClickListener { save() }

        updateDateTimeButtons()

        if (entryId > 0L) loadEntry() else selectMood("")
    }

    // ---- 기분(이모지) ----
    private fun buildMoodPicker() {
        val size = (44 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()
        for (emoji in moodEmojis) {
            val tv = TextView(this).apply {
                text = emoji
                textSize = 22f
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_mood)
                val lp = android.widget.LinearLayout.LayoutParams(size, size)
                lp.marginEnd = margin
                layoutParams = lp
                setOnClickListener {
                    selectMood(if (mood == emoji) "" else emoji)
                }
            }
            moodViews.add(tv)
            binding.moodContainer.addView(tv)
        }
    }

    private fun selectMood(m: String) {
        mood = m
        moodViews.forEachIndexed { i, tv -> tv.isSelected = (moodEmojis[i] == m) }
    }

    // ---- 태그 ----
    private fun parseTags(raw: String): List<String> =
        raw.split(",").map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }.distinct()

    private fun renderTagChips(tags: List<String>) {
        binding.chipTags.removeAllViews()
        for (t in tags) {
            val chip = Chip(this).apply {
                text = "#$t"
                isClickable = false
                isCheckable = false
            }
            binding.chipTags.addView(chip)
        }
        binding.chipTags.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---- 사진 ----
    private fun importPhotos(uris: List<android.net.Uri>) {
        lifecycleScope.launch {
            val names = withContext(Dispatchers.IO) {
                uris.mapNotNull { ImageStore.importImage(this@EntryEditorActivity, it) }
            }
            photos.addAll(names)
            photoAdapter.notifyDataSetChanged()
            updatePhotoVisibility()
        }
    }

    private fun removePhoto(pos: Int) {
        if (pos !in photos.indices) return
        val name = photos.removeAt(pos)
        if (name !in originalPhotos) ImageStore.delete(this, name)
        photoAdapter.notifyDataSetChanged()
        updatePhotoVisibility()
    }

    private fun updatePhotoVisibility() {
        binding.recyclerPhotos.visibility = if (photos.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---- 날짜/시간 (시작 ~ 종료) ----
    private fun pickStartDate() {
        val d: LocalDate = DateUtil.toDate(dateEpochDay)
        DatePickerDialog(
            this,
            { _, y, m, day ->
                val old = dateEpochDay
                dateEpochDay = LocalDate.of(y, m + 1, day).toEpochDay()
                // 시작을 옮기면 종료도 같은 간격만큼 따라 옮긴다.
                val span = (endDateEpochDay - old).coerceAtLeast(0L)
                endDateEpochDay = dateEpochDay + span
                normalize()
                updateDateTimeButtons()
            },
            d.year, d.monthValue - 1, d.dayOfMonth
        ).show()
    }

    private fun pickEndDate() {
        val base = if (endDateEpochDay >= dateEpochDay) endDateEpochDay else dateEpochDay
        val d: LocalDate = DateUtil.toDate(base)
        val dlg = DatePickerDialog(
            this,
            { _, y, m, day ->
                endDateEpochDay = LocalDate.of(y, m + 1, day).toEpochDay()
                normalize()
                updateDateTimeButtons()
            },
            d.year, d.monthValue - 1, d.dayOfMonth
        )
        // 종료가 시작보다 앞설 수 없게 (기기 시간대 기준 자정)
        val sd = DateUtil.toDate(dateEpochDay)
        val cal = Calendar.getInstance().apply {
            set(sd.year, sd.monthValue - 1, sd.dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        dlg.datePicker.minDate = cal.timeInMillis
        dlg.show()
    }

    private fun pickStartTime() {
        if (binding.switchAllDay.isChecked) {
            toast("‘종일’을 끄면 시간을 지정할 수 있어요.")
            return
        }
        val initH = if (timeMinutes >= 0) timeMinutes / 60 else 9
        val initM = if (timeMinutes >= 0) timeMinutes % 60 else 0
        TimePickerDialog(
            this,
            { _, h, m ->
                timeMinutes = h * 60 + m
                if (endTimeMinutes < 0) endTimeMinutes = (timeMinutes + 60).coerceAtMost(23 * 60 + 59)
                normalize()
                updateDateTimeButtons()
            },
            initH, initM, false
        ).show()
    }

    private fun pickEndTime() {
        if (binding.switchAllDay.isChecked) {
            toast("‘종일’을 끄면 시간을 지정할 수 있어요.")
            return
        }
        val base = if (endTimeMinutes >= 0) endTimeMinutes else (timeMinutes.coerceAtLeast(0) + 60)
        val initH = (base / 60).coerceIn(0, 23)
        val initM = base % 60
        TimePickerDialog(
            this,
            { _, h, m ->
                endTimeMinutes = h * 60 + m
                normalize()
                updateDateTimeButtons()
            },
            initH, initM, false
        ).show()
    }

    /** 종료가 시작보다 앞서지 않도록 정리 */
    private fun normalize() {
        if (endDateEpochDay < dateEpochDay) endDateEpochDay = dateEpochDay
        if (endDateEpochDay == dateEpochDay &&
            timeMinutes >= 0 && endTimeMinutes >= 0 && endTimeMinutes < timeMinutes
        ) {
            endTimeMinutes = timeMinutes
        }
    }

    private fun updateDateTimeButtons() {
        val allDay = timeMinutes < 0
        suppressAllDayCallback = true
        binding.switchAllDay.isChecked = allDay
        suppressAllDayCallback = false

        binding.btnDate.text = DateUtil.formatShortDateWithLunar(dateEpochDay) +
            " (${DateUtil.weekdayShort(dateEpochDay)})"
        binding.btnEndDate.text = DateUtil.formatShortDateWithLunar(endDateEpochDay) +
            " (${DateUtil.weekdayShort(endDateEpochDay)})"

        binding.btnTime.isEnabled = !allDay
        binding.btnEndTime.isEnabled = !allDay
        binding.btnTime.text = if (allDay) "종일" else DateUtil.formatTime(timeMinutes)
        binding.btnEndTime.text = when {
            allDay -> "종일"
            endTimeMinutes >= 0 -> DateUtil.formatTime(endTimeMinutes)
            else -> "지정 안 함"
        }

        val days = (endDateEpochDay - dateEpochDay).toInt() + 1
        val summary = DateUtil.formatRangeLong(dateEpochDay, timeMinutes, endDateEpochDay, endTimeMinutes) +
            if (days > 1) "   ($days일간)" else ""
        binding.tvRangeSummary.text = summary
    }

    // ---- 알림 ----
    private fun defaultReminderMillis(): Long {
        val cal = Calendar.getInstance()
        val d = DateUtil.toDate(dateEpochDay)
        cal.set(d.year, d.monthValue - 1, d.dayOfMonth,
            if (timeMinutes >= 0) timeMinutes / 60 else 9,
            if (timeMinutes >= 0) timeMinutes % 60 else 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun pickReminder() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (reminderAtMillis > 0) reminderAtMillis else defaultReminderMillis()
        }
        DatePickerDialog(
            this,
            { _, y, m, day ->
                TimePickerDialog(
                    this,
                    { _, h, min ->
                        val c = Calendar.getInstance()
                        c.set(y, m, day, h, min, 0)
                        c.set(Calendar.MILLISECOND, 0)
                        reminderAtMillis = c.timeInMillis
                        updateReminderButton()
                        if (reminderAtMillis < System.currentTimeMillis()) {
                            toast("지난 시간이라 알림이 울리지 않을 수 있어요.")
                        }
                    },
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false
                ).show()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateReminderButton() {
        val cal = Calendar.getInstance().apply { timeInMillis = reminderAtMillis }
        val ed = LocalDate.of(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        ).toEpochDay()
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        binding.btnReminderTime.text = "${DateUtil.formatFullDate(ed)}  ${DateUtil.formatTime(minutes)}"
    }

    // ---- 불러오기 ----
    private fun loadEntry() {
        lifecycleScope.launch {
            val e = dao.getById(entryId) ?: return@launch
            dateEpochDay = e.dateEpochDay
            timeMinutes = e.timeMinutes
            endDateEpochDay = if (e.endDateEpochDay > e.dateEpochDay) e.endDateEpochDay else e.dateEpochDay
            endTimeMinutes = e.endTimeMinutes
            createdAt = e.createdAt
            reminderAtMillis = e.reminderAtMillis
            binding.etTitle.setText(e.title)
            binding.etContent.setText(e.content)
            selectMood(e.mood)
            binding.sliderImportance.value = e.importance.coerceIn(1, 100).toFloat()
            binding.tvImportance.text = "중요도 ${e.importance}%"
            binding.etTags.setText(e.tags.joinToString(", "))
            originalPhotos.clear(); originalPhotos.addAll(e.photos)
            photos.clear(); photos.addAll(e.photos)
            photoAdapter.notifyDataSetChanged()
            updatePhotoVisibility()
            val hasReminder = e.reminderAtMillis > 0L
            binding.switchReminder.isChecked = hasReminder
            binding.btnReminderTime.visibility = if (hasReminder) View.VISIBLE else View.GONE
            if (hasReminder) updateReminderButton()
            updateDateTimeButtons()
        }
    }

    // ---- 저장/삭제 ----
    private fun save() {
        val title = binding.etTitle.text?.toString()?.trim() ?: ""
        val content = binding.etContent.text?.toString()?.trim() ?: ""
        if (title.isEmpty() && content.isEmpty() && photos.isEmpty()) {
            toast("제목이나 내용을 입력하세요.")
            return
        }
        val tags = parseTags(binding.etTags.text?.toString() ?: "")
        val importance = binding.sliderImportance.value.toInt().coerceIn(1, 100)
        val now = System.currentTimeMillis()
        normalize()
        val entry = DiaryEntry(
            id = entryId,
            dateEpochDay = dateEpochDay,
            timeMinutes = timeMinutes,
            endDateEpochDay = endDateEpochDay,
            endTimeMinutes = if (timeMinutes < 0) -1 else endTimeMinutes,
            title = title,
            content = content,
            mood = mood,
            importance = importance,
            tags = tags,
            photos = photos.toList(),
            reminderAtMillis = if (binding.switchReminder.isChecked) reminderAtMillis else 0L,
            createdAt = if (entryId > 0L) createdAt else now,
            updatedAt = now
        )

        lifecycleScope.launch {
            val savedEntry: DiaryEntry = if (entryId > 0L) {
                dao.update(entry)
                entry
            } else {
                val newId = dao.insert(entry)
                entry.copy(id = newId)
            }
            // 원본에 있었지만 지금은 없는 사진 파일 삭제
            for (name in originalPhotos) {
                if (name !in photos) ImageStore.delete(this@EntryEditorActivity, name)
            }
            ReminderScheduler.scheduleEntry(this@EntryEditorActivity, savedEntry)
            WidgetUpdater.refreshAll(this@EntryEditorActivity)
            saved = true
            toast("저장되었습니다.")
            finish()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("삭제")
            .setMessage("이 일기를 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ -> doDelete() }
            .show()
    }

    private fun doDelete() {
        lifecycleScope.launch {
            val e = dao.getById(entryId) ?: return@launch
            dao.delete(e)
            e.photos.forEach { ImageStore.delete(this@EntryEditorActivity, it) }
            ReminderScheduler.cancelEntry(this@EntryEditorActivity, entryId)
            WidgetUpdater.refreshAll(this@EntryEditorActivity)
            saved = true
            toast("삭제되었습니다.")
            finish()
        }
    }

    override fun onDestroy() {
        // 저장하지 않고 '나갈 때'만 이번에 새로 추가한 사진 파일을 정리한다.
        // (화면 회전 등으로 잠시 없어지는 경우에는 지우면 안 된다)
        if (!saved && isFinishing) {
            for (name in photos) {
                if (name !in originalPhotos) ImageStore.delete(this, name)
            }
        }
        super.onDestroy()
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        menu.findItem(R.id.action_delete)?.isVisible = entryId > 0L
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_delete -> { confirmDelete(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
