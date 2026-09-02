package com.jooshin.diary.ui

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jooshin.diary.R
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.data.countsByDay
import com.jooshin.diary.databinding.ActivityMainBinding
import com.jooshin.diary.util.AppLock
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.KoreanHolidays
import com.jooshin.diary.util.LunarCalendar
import com.jooshin.diary.util.Palette
import com.jooshin.diary.util.Prefs
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: EntryAdapter
    private val dao by lazy { AppDatabase.get(this).diaryDao() }

    private val palette by lazy { Palette.of(this) }
    private var appliedTheme: String = ""

    private var currentMonthFirst = DateUtil.firstOfMonthOf(DateUtil.today())
    private var selectedDay = DateUtil.today()

    // 목록을 위아래로 드래그할 때 달력 영역(headerContainer)을 같이 접었다 펼치기 위한 상태
    private var headerFullHeight = 0
    private var headerCollapsed = false
    private var headerAnimator: ValueAnimator? = null

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.appTheme(this).styleRes)
        appliedTheme = Prefs.themeKey(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = EntryAdapter { openEditor(it.id) }
        binding.recyclerEntries.layoutManager = LinearLayoutManager(this)
        binding.recyclerEntries.adapter = adapter

        binding.calendarView.onDaySelected = { handleSelect(it) }
        binding.calendarView.onSwipeMonth = { dir ->
            currentMonthFirst = DateUtil.addMonths(currentMonthFirst, dir)
            loadMonth()
        }

        binding.btnPrevMonth.setOnClickListener {
            currentMonthFirst = DateUtil.addMonths(currentMonthFirst, -1)
            loadMonth()
        }
        binding.btnNextMonth.setOnClickListener {
            currentMonthFirst = DateUtil.addMonths(currentMonthFirst, +1)
            loadMonth()
        }
        binding.tvMonthTitle.setOnClickListener { showMonthPicker() }
        binding.btnToday.setOnClickListener {
            currentMonthFirst = DateUtil.firstOfMonthOf(DateUtil.today())
            selectedDay = DateUtil.today()
            loadMonth()
            loadEntries()
        }
        binding.fabAdd.setOnClickListener { openEditorNew(selectedDay) }

        setupHeaderCollapse()
        maybeRequestNotifPermission()
    }

    /**
     * 일기 목록(recyclerEntries)을 위로 드래그하면 달력 영역(headerContainer)이 같이 접히고,
     * 목록을 아래로 당기거나 맨 위까지 올리면 다시 펼쳐진다.
     *
     * 전에는 CoordinatorLayout + AppBarLayout 의 스크롤 연동 기능에 맡겼었는데, 실제로는
     * AppBarLayout 자체의 "직접 드래그하면 접히는" 내장 동작이 달력의 좌우 스와이프(월 이동)
     * 제스처와 터치를 서로 먼저 가로채려고 경합하는 문제가 있었다. 그래서 여기서는
     * CoordinatorLayout 을 쓰지 않고, 목록의 스크롤 방향을 직접 보고 애니메이션으로
     * 접었다 펼치는 방식으로 바꿨다. (이러면 달력 스와이프와 전혀 부딪히지 않는다)
     */
    private fun setupHeaderCollapse() {
        binding.headerContainer.post {
            if (headerFullHeight <= 0) headerFullHeight = binding.headerContainer.height
        }
        binding.recyclerEntries.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (headerFullHeight <= 0) return
                if (!rv.canScrollVertically(-1)) {
                    expandHeader()
                    return
                }
                if (dy > 4 && !headerCollapsed) collapseHeader()
                else if (dy < -4 && headerCollapsed) expandHeader()
            }
        })
        // 목록이 맨 위까지 스크롤된 다음에도 계속 아래로 당기면(더 스크롤할 곳이 없는 상태),
        // onScrolled 만으로는 그 "당기는 동작" 자체를 알 수가 없다(목록이 실제로 움직이지
        // 않으니까). 그래서 이 "남은 드래그량"은 MainRootLayout 이 중첩 스크롤로 따로 알려주고,
        // 그 값만큼 손가락을 따라 달력을 실시간으로 펼친다.
        binding.mainRoot.onOverscrollDown = { extraPx ->
            if (headerFullHeight > 0) {
                headerAnimator?.cancel()
                val newHeight = (binding.headerContainer.height + extraPx).coerceIn(0, headerFullHeight)
                val lp = binding.headerContainer.layoutParams
                lp.height = newHeight
                binding.headerContainer.layoutParams = lp
                if (newHeight >= headerFullHeight) headerCollapsed = false
            }
        }
    }

    private fun collapseHeader() {
        headerCollapsed = true
        animateHeaderTo(0)
    }

    private fun expandHeader() {
        headerCollapsed = false
        animateHeaderTo(headerFullHeight)
    }

    private fun animateHeaderTo(target: Int) {
        if (headerFullHeight <= 0) return
        val current = binding.headerContainer.height
        if (current == target && headerAnimator?.isRunning != true) return
        headerAnimator?.cancel()
        headerAnimator = ValueAnimator.ofInt(current, target).apply {
            duration = 180
            addUpdateListener { a ->
                val v = a.animatedValue as Int
                val lp = binding.headerContainer.layoutParams
                lp.height = v
                binding.headerContainer.layoutParams = lp
            }
            start()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 설정에서 디자인을 바꿨으면 화면을 다시 만들어 즉시 반영
        if (appliedTheme != Prefs.themeKey(this)) {
            recreate()
            return
        }
        if (AppLock.isLockRequired(this)) {
            startActivity(Intent(this, LockActivity::class.java))
            return
        }
        // 상대가 올린 내용이 도착하면 화면을 새로 그린다

        loadMonth()
        loadEntries()
        handleIntentExtras()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun handleSelect(day: Long) {
        selectedDay = day
        val m = DateUtil.firstOfMonthOf(day)
        if (m != currentMonthFirst) {
            currentMonthFirst = m
            loadMonth()
        } else {
            binding.calendarView.setSelected(day)
        }
        loadEntries()
    }

    private fun loadMonth() {
        val gridStart = DateUtil.monthGridStart(currentMonthFirst)
        val gridEnd = gridStart + 41
        lifecycleScope.launch {
            val counts = dao.getOverlapping(gridStart, gridEnd).countsByDay(gridStart, gridEnd)
            binding.calendarView.bind(currentMonthFirst, selectedDay, counts)
            binding.tvMonthTitle.text = DateUtil.formatMonthTitle(currentMonthFirst)
        }
    }

    /** 좌상단 "2026년 8월" 을 눌렀을 때: 년/월을 바로 골라 이동하는 다이얼로그. */
    private fun showMonthPicker() {
        val cur = DateUtil.toDate(currentMonthFirst)
        val yearPicker = NumberPicker(this).apply {
            minValue = 1900
            maxValue = 2049
            value = cur.year.coerceIn(minValue, maxValue)
            wrapSelectorWheel = false
        }
        val monthPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 12
            value = cur.monthValue
            wrapSelectorWheel = true
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), 0)
            addView(yearPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(monthPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        AlertDialog.Builder(this)
            .setTitle("년/월로 이동")
            .setView(row)
            .setNegativeButton("취소", null)
            .setPositiveButton("이동") { _, _ ->
                currentMonthFirst = DateUtil.firstOfMonth(yearPicker.value, monthPicker.value)
                loadMonth()
            }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun loadEntries() {
        binding.tvSelectedDate.text = DateUtil.formatFullDate(selectedDay)
        val info = KoreanHolidays.info(selectedDay)
        val red = DateUtil.dowIndex(selectedDay) == 0 || info.isHoliday
        binding.tvSelectedDate.setTextColor(
            when {
                red -> palette.sun
                DateUtil.dowIndex(selectedDay) == 6 -> palette.sat
                else -> palette.textPrimary
            }
        )
        // 음력은 양력 날짜 옆에 나란히
        val lunar = LunarCalendar.longLabel(selectedDay)
        binding.tvSelectedLunar.text = lunar
        binding.tvSelectedLunar.visibility =
            if (lunar.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE

        // 공휴일/기념일 이름은 그 아래 줄에
        binding.tvSelectedInfo.text = info.full
        binding.tvSelectedInfo.visibility =
            if (info.full.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        binding.tvSelectedInfo.setTextColor(
            if (info.isHoliday) palette.sun else palette.textMuted
        )

        adapter.refDay = selectedDay
        lifecycleScope.launch {
            val list = dao.getForDay(selectedDay)
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun handleIntentExtras() {
        val i = intent ?: return
        val entryId = i.getLongExtra(EXTRA_ENTRY_ID, 0L)
        val date = i.getLongExtra(EXTRA_DATE, Long.MIN_VALUE)
        val isNew = i.getBooleanExtra(EXTRA_NEW, false)
        val time = i.getIntExtra(EXTRA_TIME, -1)
        i.removeExtra(EXTRA_ENTRY_ID)
        i.removeExtra(EXTRA_DATE)
        i.removeExtra(EXTRA_NEW)
        i.removeExtra(EXTRA_TIME)

        if (entryId > 0L) {
            openEditor(entryId)
            return
        }
        if (date != Long.MIN_VALUE) {
            handleSelect(date)
            if (isNew) openEditorNew(date, time)
        }
    }

    private fun openEditorNew(day: Long, time: Int = -1) {
        startActivity(Intent(this, EntryEditorActivity::class.java).apply {
            putExtra(EXTRA_DATE, day)
            putExtra(EXTRA_NEW, true)
            if (time >= 0) putExtra(EXTRA_TIME, time)
        })
    }

    private fun openEditor(id: Long) {
        startActivity(Intent(this, EntryEditorActivity::class.java).apply {
            putExtra(EXTRA_ENTRY_ID, id)
        })
    }

    private fun maybeRequestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !Prefs.isFirstRunDone(this)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Prefs.setFirstRunDone(this)
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // 어두운 상단바 위에서 잘 보이도록 아이콘을 흰색으로
        // (mutate() 로 복사해서 칠해야 다른 화면의 같은 아이콘까지 흰색이 되지 않는다)
        val white = ContextCompat.getColor(this, R.color.white)
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon = menu.getItem(i).icon?.mutate()?.also { it.setTint(white) }
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                startActivity(Intent(this, SearchActivity::class.java)); true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_ENTRY_ID = "extra_entry_id"
        const val EXTRA_NEW = "extra_new"

        /** 새 일기의 시작 시각(자정부터의 분). 일 위젯의 시간대 탭에서 사용. */
        const val EXTRA_TIME = "extra_time"
    }
}
