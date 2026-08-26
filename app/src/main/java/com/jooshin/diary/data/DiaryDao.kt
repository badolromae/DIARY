package com.jooshin.diary.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/** 하루에 몇 개의 일기가 있는지(달력 점 표시용) */
data class DayCount(val dateEpochDay: Long, val cnt: Int)

/**
 * 기간(시작~종료) 일정을 지원하므로, '그 날의 일정' 은
 *   시작 <= 그날  AND  종료(없으면 시작) >= 그날
 * 조건으로 찾는다.
 */
@Dao
interface DiaryDao {

    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Update
    suspend fun update(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): DiaryEntry?

    // ---- 하루 ----
    @Query(
        "SELECT * FROM entries " +
            "WHERE dateEpochDay <= :day AND MAX(dateEpochDay, endDateEpochDay) >= :day " +
            "ORDER BY CASE WHEN dateEpochDay < :day THEN 0 WHEN timeMinutes < 0 THEN 2 ELSE 1 END, " +
            "timeMinutes ASC, createdAt ASC"
    )
    suspend fun getForDay(day: Long): List<DiaryEntry>

    @Query(
        "SELECT * FROM entries " +
            "WHERE dateEpochDay <= :day AND MAX(dateEpochDay, endDateEpochDay) >= :day " +
            "ORDER BY CASE WHEN dateEpochDay < :day THEN 0 WHEN timeMinutes < 0 THEN 2 ELSE 1 END, " +
            "timeMinutes ASC, createdAt ASC"
    )
    fun observeForDay(day: Long): LiveData<List<DiaryEntry>>

    // ---- 기간 (달력 그리드 / 주 위젯) ----
    @Query(
        "SELECT * FROM entries " +
            "WHERE dateEpochDay <= :end AND MAX(dateEpochDay, endDateEpochDay) >= :start " +
            "ORDER BY dateEpochDay ASC, CASE WHEN timeMinutes < 0 THEN 1 ELSE 0 END, " +
            "timeMinutes ASC, createdAt ASC"
    )
    suspend fun getOverlapping(start: Long, end: Long): List<DiaryEntry>

    @Query("SELECT * FROM entries ORDER BY dateEpochDay DESC, CASE WHEN timeMinutes < 0 THEN 1 ELSE 0 END, timeMinutes DESC")
    fun observeAll(): LiveData<List<DiaryEntry>>

    @Query(
        "SELECT * FROM entries WHERE title LIKE '%' || :q || '%' " +
            "OR content LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' " +
            "ORDER BY dateEpochDay DESC, timeMinutes ASC"
    )
    suspend fun search(q: String): List<DiaryEntry>

    @Query("SELECT * FROM entries WHERE reminderAtMillis > 0")
    suspend fun getWithReminders(): List<DiaryEntry>

    // ---- 위젯용 동기 쿼리(백그라운드 스레드에서만 호출) ----
    @Query(
        "SELECT * FROM entries " +
            "WHERE dateEpochDay <= :day AND MAX(dateEpochDay, endDateEpochDay) >= :day " +
            "ORDER BY CASE WHEN dateEpochDay < :day THEN 0 WHEN timeMinutes < 0 THEN 2 ELSE 1 END, " +
            "timeMinutes ASC, createdAt ASC"
    )
    fun getForDaySync(day: Long): List<DiaryEntry>

    @Query(
        "SELECT * FROM entries " +
            "WHERE dateEpochDay <= :end AND MAX(dateEpochDay, endDateEpochDay) >= :start " +
            "ORDER BY dateEpochDay ASC, CASE WHEN timeMinutes < 0 THEN 1 ELSE 0 END, " +
            "timeMinutes ASC, createdAt ASC"
    )
    fun getOverlappingSync(start: Long, end: Long): List<DiaryEntry>
}
