package com.jooshin.diary.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 날짜/시간 계산 및 한국어 포맷 헬퍼. (java.time, minSdk 26) */
object DateUtil {
    private val KR = Locale.KOREA
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", KR)

    fun today(): Long = LocalDate.now().toEpochDay()

    fun toDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun firstOfMonth(year: Int, month1to12: Int): Long =
        LocalDate.of(year, month1to12, 1).toEpochDay()

    fun firstOfMonthOf(epochDay: Long): Long {
        val d = toDate(epochDay)
        return LocalDate.of(d.year, d.month, 1).toEpochDay()
    }

    /** firstOfMonth 기준 delta 개월 이동한 '그 달 1일' */
    fun addMonths(firstOfMonthEpochDay: Long, delta: Int): Long {
        val d = toDate(firstOfMonthEpochDay)
        return d.plusMonths(delta.toLong()).withDayOfMonth(1).toEpochDay()
    }

    /** 일요일 시작 기준, 해당 주의 일요일 epochDay */
    fun weekStart(epochDay: Long): Long {
        val d = toDate(epochDay)
        val offset = d.dayOfWeek.value % 7 // MON=1..SUN=7 -> SUN=0, MON=1 ... SAT=6
        return epochDay - offset
    }

    /** 월 달력 그리드의 첫 칸(그 달 1일이 포함된 주의 일요일) */
    fun monthGridStart(firstOfMonthEpochDay: Long): Long = weekStart(firstOfMonthEpochDay)

    fun formatFullDate(epochDay: Long): String {
        val d = toDate(epochDay)
        val w = d.dayOfWeek.getDisplayName(TextStyle.SHORT, KR)
        return "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일 ($w)"
    }

    /** "8월 26일 (수) · 음7.14" 처럼 음력까지 */
    fun formatFullDateWithLunar(epochDay: Long): String {
        val lunar = LunarCalendar.shortLabel(epochDay)
        return if (lunar.isEmpty()) formatFullDate(epochDay)
        else "${formatFullDate(epochDay)}  ·  $lunar"
    }

    fun formatMonthTitle(firstOfMonthEpochDay: Long): String {
        val d = toDate(firstOfMonthEpochDay)
        return "${d.year}년 ${d.monthValue}월"
    }

    fun formatShortDate(epochDay: Long): String {
        val d = toDate(epochDay)
        return "${d.monthValue}/${d.dayOfMonth}"
    }

    /** "10/1(음9.1)" 처럼 날짜 옆에 음력을 붙인 짧은 표기 */
    fun formatShortDateWithLunar(epochDay: Long): String {
        val lunar = LunarCalendar.shortLabel(epochDay)
        return if (lunar.isEmpty()) formatShortDate(epochDay)
        else "${formatShortDate(epochDay)}($lunar)"
    }

    fun weekdayShort(epochDay: Long): String =
        toDate(epochDay).dayOfWeek.getDisplayName(TextStyle.SHORT, KR)

    /** "월요일" 처럼 요일 전체 이름 */
    fun weekdayFull(epochDay: Long): String =
        toDate(epochDay).dayOfWeek.getDisplayName(TextStyle.FULL, KR)

    /** "8/24 월요일" */
    fun formatDateWeekdayFull(epochDay: Long): String =
        "${formatShortDate(epochDay)} ${weekdayFull(epochDay)}"

    /** 0~23 시를 "오전 9시" / "오후 3시" 로 */
    fun formatHour(hour: Int): String = when {
        hour == 0 -> "오전 12시"
        hour < 12 -> "오전 ${hour}시"
        hour == 12 -> "오후 12시"
        else -> "오후 ${hour - 12}시"
    }

    fun formatTime(timeMinutes: Int): String {
        if (timeMinutes < 0) return "종일"
        val t = LocalTime.of(timeMinutes / 60, timeMinutes % 60)
        return t.format(timeFmt)
    }

    /** 0=일요일 ... 6=토요일 */
    fun dowIndex(epochDay: Long): Int = toDate(epochDay).dayOfWeek.value % 7

    // ------------------------------------------------------------------
    // 기간(시작 ~ 종료) 표기
    // ------------------------------------------------------------------

    /**
     * 목록/위젯의 왼쪽 시간 칸에 넣을 짧은 표기.
     * - 하루 · 시간 있음 : "오전 9:00"  (끝 시간 있으면 두 줄로 "오전 9:00\n~ 오후 6:00")
     * - 하루 · 종일     : "종일"
     * - 여러 날         : "8/26~\n8/28" 형태
     */
    fun formatTimeRangeShort(
        startDay: Long, startTime: Int, endDay: Long, endTime: Int
    ): String {
        val multiDay = endDay > startDay
        return when {
            multiDay -> "${formatShortDate(startDay)} ~\n${formatShortDate(endDay)}"
            startTime < 0 -> "종일"
            endTime >= 0 && endTime != startTime -> "${formatTime(startTime)}\n~ ${formatTime(endTime)}"
            else -> formatTime(startTime)
        }
    }

    /** 편집 화면/상세용 한 줄 표기 */
    fun formatRangeLong(
        startDay: Long, startTime: Int, endDay: Long, endTime: Int
    ): String {
        val sameDay = endDay <= startDay
        val s = formatFullDate(startDay) + if (startTime >= 0) " ${formatTime(startTime)}" else ""
        if (sameDay) {
            return if (startTime >= 0 && endTime >= 0 && endTime != startTime)
                "$s ~ ${formatTime(endTime)}" else if (startTime < 0) "${formatFullDate(startDay)} 종일" else s
        }
        val e = formatFullDate(endDay) + if (endTime >= 0) " ${formatTime(endTime)}" else ""
        return "$s  ~  $e"
    }
}
