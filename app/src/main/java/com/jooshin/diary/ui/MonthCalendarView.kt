package com.jooshin.diary.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.jooshin.diary.R
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.KoreanHolidays
import com.jooshin.diary.util.LunarCalendar
import com.jooshin.diary.util.Palette
import kotlin.math.abs

/**
 * 앱 메인 화면의 월 달력(6주 x 7일).
 * 각 칸: 날짜 / 기록 표시 막대 / 음력 / 공휴일·기념일 이름
 * 일요일과 공휴일(대체공휴일 포함)은 같은 빨간색으로 표시한다.
 */
class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onDaySelected: ((Long) -> Unit)? = null

    /** 좌우로 스와이프했을 때 호출. -1 = 이전 달, +1 = 다음 달. */
    var onSwipeMonth: ((Int) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var swiping = false

    private val dayViews = ArrayList<TextView>(42)
    private val lunarViews = ArrayList<TextView>(42)
    private val noteViews = ArrayList<TextView>(42)
    private val dotViews = ArrayList<View>(42)
    private val epochDays = LongArray(42)

    private var firstOfMonth = DateUtil.firstOfMonthOf(DateUtil.today())
    private var selected = DateUtil.today()
    private var counts: Map<Long, Int> = emptyMap()
    private val palette by lazy { Palette.of(context) }

    init {
        orientation = VERTICAL
        addView(buildWeekdayHeader())
        for (w in 0 until 6) addView(buildWeekRow())
        renderDates()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // 좌우 스와이프로 월 이동: 가로로 크게 움직이면 이 뷰가 터치를 가로채 스와이프로 처리하고,
    // 그렇지 않으면(가벼운 탭이면) 평소처럼 날짜 칸의 클릭이 그대로 동작한다.
    // (예전엔 GestureDetector 의 onFling 속도 판정에 맡겼는데, 손가락을 빠르게 튕기지 않으면
    //  인식이 안 되는 경우가 있어서, 손을 뗄 때의 실제 이동 거리만으로 더 확실하게 판정한다.)
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; swiping = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!swiping && abs(dx) > touchSlop && abs(dx) > abs(dy)) swiping = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {} // 최종 판정은 onTouchEvent 에서
        }
        return swiping
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!swiping && abs(dx) > touchSlop && abs(dx) > abs(dy)) swiping = true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (swiping && abs(dx) > dp(60) && abs(dx) > abs(dy)) {
                    onSwipeMonth?.invoke(if (dx < 0) 1 else -1)
                }
                swiping = false
            }
            MotionEvent.ACTION_CANCEL -> swiping = false
        }
        return true
    }

    private fun buildWeekdayHeader(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        val labels = listOf("일", "월", "화", "수", "목", "금", "토")
        for (i in 0..6) {
            val tv = TextView(context).apply {
                text = labels[i]
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(0, dp(6), 0, dp(6))
                setTextColor(
                    when (i) {
                        0 -> palette.sun
                        6 -> palette.sat
                        else -> palette.textMuted
                    }
                )
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(tv)
        }
        return row
    }

    private fun buildWeekRow(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(68))
        }
        for (c in 0..6) {
            val cell = FrameLayout(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).also {
                    it.setMargins(dp(1), dp(1), dp(1), dp(1))
                }
                isClickable = true
                // 날짜별 구분선 (둥근 모서리 테두리)
                setBackgroundResource(R.drawable.cal_cell_border)
                val rippleRes = selectableItemBackgroundRes()
                if (rippleRes != 0) {
                    foreground = ContextCompat.getDrawable(context, rippleRes)
                }
            }
            val box = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setPadding(dp(1), dp(2), dp(1), 0)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            val day = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
            }
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(3)).also { it.topMargin = dp(1) }
            }
            val lunar = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 10f
                maxLines = 1
                includeFontPadding = false
                setTextColor(palette.calLunar)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(1) }
            }
            val note = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 10f
                maxLines = 1
                includeFontPadding = false
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            box.addView(day)
            box.addView(dot)
            box.addView(lunar)
            box.addView(note)
            cell.addView(box)
            val index = dayViews.size
            cell.setOnClickListener { handleClick(index) }
            dayViews.add(day)
            dotViews.add(dot)
            lunarViews.add(lunar)
            noteViews.add(note)
            row.addView(cell)
        }
        return row
    }

    private fun selectableItemBackgroundRes(): Int {
        val out = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return out.resourceId
    }

    private fun handleClick(index: Int) {
        val ed = epochDays[index]
        selected = ed
        val newMonth = DateUtil.firstOfMonthOf(ed)
        if (newMonth != firstOfMonth) firstOfMonth = newMonth
        renderDates()
        onDaySelected?.invoke(ed)
    }

    fun bind(firstOfMonthEpochDay: Long, selectedEpochDay: Long, counts: Map<Long, Int>) {
        this.firstOfMonth = firstOfMonthEpochDay
        this.selected = selectedEpochDay
        this.counts = counts
        renderDates()
    }

    fun setSelected(epochDay: Long) {
        selected = epochDay
        renderStates()
    }

    private fun renderDates() {
        val gridStart = DateUtil.monthGridStart(firstOfMonth)
        for (i in 0..41) epochDays[i] = gridStart + i
        renderStates()
    }

    private fun renderStates() {
        val monthVal = DateUtil.toDate(firstOfMonth).monthValue
        val today = DateUtil.today()
        val cOutside = palette.calOutside
        val cSun = palette.sun
        val cSat = palette.sat
        val cNormal = palette.calNormal
        val cSel = palette.onSelected
        val cLunar = palette.calLunar
        val cMuted = palette.textMuted

        for (i in 0..41) {
            val ed = epochDays[i]
            val d = DateUtil.toDate(ed)
            val tv = dayViews[i]
            tv.text = d.dayOfMonth.toString()
            val inMonth = d.monthValue == monthVal
            val isSel = ed == selected
            val isToday = ed == today
            val dow = DateUtil.dowIndex(ed)
            val info = KoreanHolidays.info(ed)
            val red = dow == 0 || info.isHoliday

            when {
                isSel -> tv.setBackgroundResource(R.drawable.bg_day_selected)
                isToday -> tv.setBackgroundResource(R.drawable.bg_day_today)
                else -> tv.background = null
            }
            tv.setTextColor(
                when {
                    isSel -> cSel
                    !inMonth -> cOutside
                    red -> cSun
                    dow == 6 -> cSat
                    else -> cNormal
                }
            )

            // 매달 1일에만 음력을 항상 보여주고, 나머지 날짜는 눌렀을 때(아래 날짜 정보) 확인한다.
            lunarViews[i].text = if (d.dayOfMonth == 1) LunarCalendar.shortLabel(ed) else ""
            lunarViews[i].setTextColor(if (inMonth) cLunar else cOutside)

            val note = info.compact
            if (note.isEmpty()) {
                noteViews[i].visibility = View.GONE
            } else {
                noteViews[i].visibility = View.VISIBLE
                noteViews[i].text = note
                noteViews[i].setTextColor(
                    when {
                        !inMonth -> cOutside
                        info.isHoliday -> cSun
                        else -> cMuted
                    }
                )
            }

            val cnt = counts[ed] ?: 0
            if (cnt > 0) {
                dotViews[i].visibility = View.VISIBLE
                dotViews[i].setBackgroundResource(
                    if (isSel) R.drawable.dot_on_selected else R.drawable.dot_entry
                )
            } else {
                dotViews[i].visibility = View.INVISIBLE
            }
        }
    }
}
