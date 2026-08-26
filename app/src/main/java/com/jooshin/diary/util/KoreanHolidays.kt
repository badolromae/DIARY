package com.jooshin.diary.util

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 한국 공휴일 · 기념일.
 *
 * - 공휴일(빨간 날): 「관공서의 공휴일에 관한 규정」 기준 + 대체공휴일 자동 계산
 * - 기념일: 「각종 기념일 등에 관한 규정」의 법정기념일 + 주요 세시풍속 + 생활기념일
 *
 * 음력 공휴일(설날·부처님오신날·추석)과 세시풍속은 [LunarCalendar] 로 정확히 계산합니다.
 * 임시공휴일·선거일은 미리 알 수 없으므로 [EXTRA_HOLIDAYS] 에 직접 추가하면 됩니다.
 */
object KoreanHolidays {

    /** 그 날의 정보. holidays 가 비어있지 않으면 '빨간 날'. */
    data class DayInfo(
        val holidays: List<String> = emptyList(),
        val others: List<String> = emptyList()
    ) {
        val isHoliday: Boolean get() = holidays.isNotEmpty()

        /** 달력 칸에 한 줄로 넣을 대표 이름 */
        val short: String get() = holidays.firstOrNull() ?: others.firstOrNull() ?: ""

        /** 상세 화면용 전체 이름 */
        val full: String get() = (holidays + others).joinToString(" · ")

        val isEmpty: Boolean get() = holidays.isEmpty() && others.isEmpty()
    }

    private val EMPTY = DayInfo()

    /**
     * 임시공휴일 · 선거일처럼 미리 계산할 수 없는 공휴일.
     * "yyyy-MM-dd" 와 이름을 넣으면 자동으로 빨간 날이 됩니다.
     */
    private val EXTRA_HOLIDAYS: Map<String, String> = mapOf(
        "2028-04-12" to "국회의원선거일"
    )

    // ------------------------------------------------------------------
    // 공개 API
    // ------------------------------------------------------------------

    fun info(epochDay: Long): DayInfo =
        yearMap(LocalDate.ofEpochDay(epochDay).year)[epochDay] ?: EMPTY

    /** 법정 공휴일인가 (일요일은 제외한 판단) */
    fun isHoliday(epochDay: Long): Boolean = info(epochDay).isHoliday

    /** 달력에서 빨간색으로 칠할 날인가 = 일요일 이거나 공휴일 */
    fun isRed(epochDay: Long): Boolean =
        LocalDate.ofEpochDay(epochDay).dayOfWeek == DayOfWeek.SUNDAY || isHoliday(epochDay)

    /** 달력 칸에 넣을 짧은 이름 (없으면 "") */
    fun shortName(epochDay: Long): String = info(epochDay).short

    // ------------------------------------------------------------------
    // 연도별 캐시
    // ------------------------------------------------------------------

    private val cache = LinkedHashMap<Int, Map<Long, DayInfo>>()

    @Synchronized
    private fun yearMap(year: Int): Map<Long, DayInfo> {
        cache[year]?.let { return it }
        val built = build(year)
        if (cache.size >= 12) cache.remove(cache.keys.first())
        cache[year] = built
        return built
    }

    // ------------------------------------------------------------------
    // 계산
    // ------------------------------------------------------------------

    private class Builder {
        val holidays = LinkedHashMap<Long, MutableList<String>>()
        val others = LinkedHashMap<Long, MutableList<String>>()

        fun h(ed: Long?, name: String) {
            if (ed == null) return
            val l = holidays.getOrPut(ed) { mutableListOf() }
            if (name !in l) l.add(name)
        }

        fun e(ed: Long?, name: String) {
            if (ed == null) return
            val l = others.getOrPut(ed) { mutableListOf() }
            if (name !in l) l.add(name)
        }
    }

    private fun solar(y: Int, m: Int, d: Int): Long? = try {
        LocalDate.of(y, m, d).toEpochDay()
    } catch (t: Throwable) {
        null
    }

    private fun lunar(y: Int, m: Int, d: Int): Long? = LunarCalendar.toEpochDay(y, m, d, false)

    /** 그 달의 n 번째 특정 요일 (n 이 음수면 마지막부터) */
    private fun nthDow(y: Int, month: Int, dow: DayOfWeek, n: Int): Long? {
        return try {
            if (n > 0) {
                val first = LocalDate.of(y, month, 1)
                val shift = (dow.value - first.dayOfWeek.value + 7) % 7
                val d = first.plusDays((shift + (n - 1) * 7).toLong())
                if (d.monthValue != month) null else d.toEpochDay()
            } else {
                val last = LocalDate.of(y, month, 1).plusMonths(1).minusDays(1)
                val shift = (last.dayOfWeek.value - dow.value + 7) % 7
                last.minusDays(shift.toLong()).toEpochDay()
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun build(targetYear: Int): Map<Long, DayInfo> {
        val b = Builder()
        // 연말·연초에 걸치는 설날 연휴·섣달그믐 때문에 앞뒤 해까지 계산한 뒤 잘라낸다.
        for (y in (targetYear - 1)..(targetYear + 1)) {
            addPublicHolidays(b, y)
            addMemorialDays(b, y)
            addFolkDays(b, y)
        }
        applySubstitutes(b, targetYear)

        val out = HashMap<Long, DayInfo>()
        val keys = b.holidays.keys + b.others.keys
        for (ed in keys) {
            if (LocalDate.ofEpochDay(ed).year != targetYear) continue
            out[ed] = DayInfo(
                holidays = b.holidays[ed]?.toList() ?: emptyList(),
                others = b.others[ed]?.toList() ?: emptyList()
            )
        }
        return out
    }

    // ---- 공휴일 ----
    private fun addPublicHolidays(b: Builder, y: Int) {
        b.h(solar(y, 1, 1), "신정")
        b.h(solar(y, 3, 1), "삼일절")
        b.h(solar(y, 5, 5), "어린이날")
        b.h(solar(y, 6, 6), "현충일")
        // 제헌절: 2026년부터 다시 공휴일
        if (y >= 2026) b.h(solar(y, 7, 17), "제헌절")
        b.h(solar(y, 8, 15), "광복절")
        b.h(solar(y, 10, 3), "개천절")
        b.h(solar(y, 10, 9), "한글날")
        b.h(solar(y, 12, 25), "성탄절")

        // 설날 (음력 1월 1일 앞뒤 하루씩)
        lunar(y, 1, 1)?.let { seol ->
            b.h(seol - 1, "설날 연휴")
            b.h(seol, "설날")
            b.h(seol + 1, "설날 연휴")
        }
        // 부처님오신날 (음력 4월 8일)
        b.h(lunar(y, 4, 8), "부처님오신날")
        // 추석 (음력 8월 15일 앞뒤 하루씩)
        lunar(y, 8, 15)?.let { chu ->
            b.h(chu - 1, "추석 연휴")
            b.h(chu, "추석")
            b.h(chu + 1, "추석 연휴")
        }

        for ((iso, name) in EXTRA_HOLIDAYS) {
            val d = try {
                LocalDate.parse(iso)
            } catch (t: Throwable) {
                null
            }
            if (d != null && d.year == y) b.h(d.toEpochDay(), name)
        }
    }

    /** 토·일에 겹치면 대체공휴일이 생기는 공휴일 */
    private val SUBSTITUTE_SAT_SUN = setOf(
        "삼일절", "어린이날", "부처님오신날", "제헌절", "광복절", "개천절", "한글날", "성탄절"
    )

    /** 일요일에 겹칠 때만 대체공휴일이 생기는 공휴일 (설·추석 연휴) */
    private val SUBSTITUTE_SUN_ONLY = setOf("설날", "설날 연휴", "추석", "추석 연휴")

    private fun applySubstitutes(b: Builder, targetYear: Int) {
        val base = b.holidays.keys.toMutableSet()
        // 연도 경계에서도 올바르게 계산되도록 정렬 후 순서대로 처리
        val days = b.holidays.keys.sorted()
        val added = LinkedHashMap<Long, String>()

        for (ed in days) {
            val names = b.holidays[ed] ?: continue
            val dow = LocalDate.ofEpochDay(ed).dayOfWeek
            for (name in names.toList()) {
                if (name.startsWith("대체공휴일")) continue
                val need = when {
                    name in SUBSTITUTE_SUN_ONLY -> dow == DayOfWeek.SUNDAY
                    name in SUBSTITUTE_SAT_SUN ->
                        dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY ||
                            (name == "어린이날" && names.size > 1)
                    else -> false
                }
                if (!need) continue

                // 다음 '비공휴일'을 찾는다. (일요일은 공휴일, 토요일은 공휴일 아님)
                var n = ed + 1
                var found = false
                for (step in 0 until 30) {
                    val isSunday = LocalDate.ofEpochDay(n).dayOfWeek == DayOfWeek.SUNDAY
                    val taken = base.contains(n) || added.containsKey(n)
                    if (!isSunday && !taken) {
                        found = true
                        break
                    }
                    n++
                }
                if (found) added[n] = "대체공휴일($name)"
            }
        }
        for ((ed, name) in added) {
            if (LocalDate.ofEpochDay(ed).year in (targetYear - 1)..(targetYear + 1)) b.h(ed, name)
        }
    }

    // ---- 세시풍속 (음력) ----
    private fun addFolkDays(b: Builder, y: Int) {
        b.e(lunar(y, 1, 15), "정월대보름")
        b.e(lunar(y, 3, 3), "삼짇날")
        b.e(lunar(y, 5, 5), "단오")
        b.e(lunar(y, 6, 15), "유두")
        b.e(lunar(y, 7, 7), "칠석")
        b.e(lunar(y, 7, 15), "백중")
        b.e(lunar(y, 9, 9), "중양절")
        LunarCalendar.lastDayOfMonth(y, 12, false)?.let { last ->
            b.e(LunarCalendar.toEpochDay(y, 12, last, false), "섣달그믐")
        }
    }

    // ---- 법정기념일 · 생활기념일 ----
    private fun addMemorialDays(b: Builder, y: Int) {
        // 1~2월
        b.e(solar(y, 2, 14), "밸런타인데이")
        b.e(solar(y, 2, 28), "2·28 민주운동 기념일")
        // 3월
        b.e(solar(y, 3, 3), "납세자의 날")
        b.e(solar(y, 3, 8), "3·8 민주의거 기념일")
        b.e(solar(y, 3, 14), "화이트데이")
        b.e(solar(y, 3, 15), "3·15 의거 기념일")
        b.e(nthDow(y, 3, DayOfWeek.WEDNESDAY, 3), "상공의 날")
        b.e(nthDow(y, 3, DayOfWeek.FRIDAY, 4), "서해수호의 날")
        // 4월
        b.e(solar(y, 4, 1), "수산인의 날")
        b.e(solar(y, 4, 3), "4·3 희생자 추념일")
        b.e(nthDow(y, 4, DayOfWeek.FRIDAY, 1), "예비군의 날")
        b.e(solar(y, 4, 5), "식목일")
        b.e(solar(y, 4, 7), "보건의 날")
        b.e(solar(y, 4, 11), "대한민국 임시정부 수립 기념일")
        b.e(solar(y, 4, 16), "국민안전의 날")
        b.e(solar(y, 4, 19), "4·19 혁명 기념일")
        b.e(solar(y, 4, 20), "장애인의 날")
        b.e(solar(y, 4, 21), "과학의 날")
        b.e(solar(y, 4, 22), "정보통신의 날")
        b.e(solar(y, 4, 25), "법의 날")
        b.e(solar(y, 4, 28), "충무공 이순신 탄신일")
        b.e(nthDow(y, 4, DayOfWeek.FRIDAY, 4), "순직의무군경의 날")
        // 5월
        b.e(solar(y, 5, 1), "근로자의 날")
        b.e(solar(y, 5, 8), "어버이날")
        b.e(solar(y, 5, 10), "유권자의 날")
        b.e(solar(y, 5, 11), "동학농민혁명 기념일")
        b.e(solar(y, 5, 15), "스승의 날")
        b.e(solar(y, 5, 18), "5·18 민주화운동 기념일")
        b.e(solar(y, 5, 19), "발명의 날")
        b.e(solar(y, 5, 21), "부부의 날")
        b.e(nthDow(y, 5, DayOfWeek.MONDAY, 3), "성년의 날")
        b.e(solar(y, 5, 25), "방재의 날")
        b.e(solar(y, 5, 27), "우주항공의 날")
        b.e(solar(y, 5, 31), "바다의 날")
        // 6월
        b.e(solar(y, 6, 1), "의병의 날")
        b.e(solar(y, 6, 5), "환경의 날")
        b.e(solar(y, 6, 9), "구강보건의 날")
        b.e(solar(y, 6, 10), "6·10 민주항쟁 기념일")
        b.e(solar(y, 6, 25), "6·25 전쟁일")
        b.e(solar(y, 6, 28), "철도의 날")
        // 7월
        b.e(solar(y, 7, 11), "인구의 날")
        b.e(nthDow(y, 7, DayOfWeek.WEDNESDAY, 2), "정보보호의 날")
        b.e(solar(y, 7, 14), "북한이탈주민의 날")
        if (y < 2026) b.e(solar(y, 7, 17), "제헌절")
        // 8월
        b.e(solar(y, 8, 8), "섬의 날")
        // 9월
        b.e(solar(y, 9, 7), "사회복지의 날")
        b.e(solar(y, 9, 7), "푸른 하늘의 날")
        b.e(solar(y, 9, 10), "자살예방의 날")
        b.e(nthDow(y, 9, DayOfWeek.SATURDAY, 3), "청년의 날")
        b.e(solar(y, 9, 21), "치매극복의 날")
        // 10월
        b.e(solar(y, 10, 1), "국군의 날")
        b.e(solar(y, 10, 2), "노인의 날")
        b.e(solar(y, 10, 5), "세계 한인의 날")
        b.e(solar(y, 10, 8), "재향군인의 날")
        b.e(solar(y, 10, 15), "스포츠의 날")
        b.e(solar(y, 10, 16), "부마민주항쟁 기념일")
        b.e(nthDow(y, 10, DayOfWeek.SATURDAY, 3), "문화의 날")
        b.e(solar(y, 10, 21), "경찰의 날")
        b.e(solar(y, 10, 24), "국제연합일")
        b.e(solar(y, 10, 28), "교정의 날")
        b.e(solar(y, 10, 29), "지방자치 및 균형발전의 날")
        b.e(nthDow(y, 10, DayOfWeek.TUESDAY, -1), "금융의 날")
        // 11월
        b.e(solar(y, 11, 3), "학생독립운동기념일")
        b.e(solar(y, 11, 9), "소방의 날")
        b.e(solar(y, 11, 11), "농업인의 날")
        b.e(solar(y, 11, 11), "빼빼로데이")
        b.e(solar(y, 11, 17), "순국선열의 날")
        // 12월
        b.e(solar(y, 12, 3), "소비자의 날")
        b.e(solar(y, 12, 5), "무역의 날")
        b.e(solar(y, 12, 24), "크리스마스이브")
        b.e(solar(y, 12, 27), "원자력 안전 및 진흥의 날")
    }
}
