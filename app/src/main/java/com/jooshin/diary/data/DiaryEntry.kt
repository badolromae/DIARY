package com.jooshin.diary.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 일기(일정) 한 건.
 * - dateEpochDay     : 시작 날짜(LocalDate.toEpochDay())
 * - timeMinutes      : 시작 시각(자정부터의 분, 0~1439). -1 이면 '종일'
 * - endDateEpochDay  : 종료 날짜. -1 또는 시작보다 이전이면 '하루짜리'로 취급
 * - endTimeMinutes   : 종료 시각. -1 이면 종료 시각 지정 없음
 * - importance       : 중요도 1~100(%)
 */
@Entity(tableName = "entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateEpochDay: Long,
    val timeMinutes: Int = -1,
    val title: String = "",
    val content: String = "",
    val mood: String = "",
    val importance: Int = 50,
    val tags: List<String> = emptyList(),
    val photos: List<String> = emptyList(),
    val reminderAtMillis: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val endDateEpochDay: Long = -1L,
    val endTimeMinutes: Int = -1
)

/** 실제 종료 날짜 (지정이 없으면 시작 날짜와 동일) */
val DiaryEntry.endDay: Long
    get() = if (endDateEpochDay > dateEpochDay) endDateEpochDay else dateEpochDay

/** 하루를 넘기는 일정인가 */
val DiaryEntry.isMultiDay: Boolean
    get() = endDay > dateEpochDay

/** 이 일정이 해당 날짜에 걸쳐 있는가 */
fun DiaryEntry.coversDay(day: Long): Boolean = dateEpochDay <= day && endDay >= day

/** 여러 날 일정에서, 해당 날짜가 몇 번째 날인지 (1부터). 걸쳐있지 않으면 0 */
fun DiaryEntry.dayIndexOf(day: Long): Int =
    if (coversDay(day)) (day - dateEpochDay).toInt() + 1 else 0

/** 총 며칠짜리 일정인가 */
val DiaryEntry.dayCount: Int
    get() = (endDay - dateEpochDay).toInt() + 1

/** [start]~[end] 구간에서 날짜별 일정 개수 (달력 점 표시용) */
fun List<DiaryEntry>.countsByDay(start: Long, end: Long): Map<Long, Int> {
    val out = HashMap<Long, Int>()
    for (e in this) {
        var d = maxOf(e.dateEpochDay, start)
        val last = minOf(e.endDay, end)
        while (d <= last) {
            out[d] = (out[d] ?: 0) + 1
            d++
        }
    }
    return out
}

/** 특정 날짜에 걸치는 일정만 골라, 그 날 보기 좋은 순서로 정렬 */
fun List<DiaryEntry>.forDay(day: Long): List<DiaryEntry> =
    filter { it.coversDay(day) }.sortedWith(
        compareBy(
            { if (it.dateEpochDay < day) 0 else if (it.timeMinutes < 0) 2 else 1 },
            { if (it.timeMinutes < 0) Int.MAX_VALUE else it.timeMinutes },
            { it.createdAt }
        )
    )
