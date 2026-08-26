package com.jooshin.diary.util

/**
 * 음력(한국) 변환기.
 *
 * 한국천문연구원(KASI) 기준 음력 데이터를 압축해 내장했습니다. (1900 ~ 2049 음력년)
 * 지원 양력 범위: 1900-01-31 ~ 2050-01-22
 *
 * NEW_YEAR[i] : 음력 (1900+i)년 1월 1일의 양력 epochDay
 * PACKED[i]   : 하위 13비트 = 그 해 각 달의 크기(1=30일, 0=29일, 순서대로)
 *               상위 4비트(13..16) = 윤달 번호(0이면 윤달 없음)
 */
object LunarCalendar {

    private const val BASE_YEAR = 1900
    const val MIN_EPOCH_DAY = -25537L      // 1900-01-31
    const val MAX_EPOCH_DAY = 28887L     // 2049년 음력 1월 1일 (이후 그 해 끝까지 지원)

    private val NEW_YEAR = intArrayOf(
        -25537, -25153, -24799, -24444, -24061, -23707, -23352, -22968, -22614, -22259, -21875, -21521,
        -21137, -20783, -20429, -20045, -19690, -19336, -18952, -18597, -18213, -17859, -17505, -17121,
        -16767, -16413, -16028, -15674, -15319, -14935, -14581, -14198, -13844, -13489, -13105, -12750,
        -12396, -12012, -11658, -11274, -10920, -10566, -10182, -9827, -9472, -9088, -8734, -8380,
        -7996, -7642, -7258, -6904, -6549, -6165, -5810, -5456, -5072, -4718, -4334, -3980,
        -3626, -3242, -2887, -2533, -2149, -1794, -1440, -1057, -702, -318, 36, 391,
        775, 1129, 1483, 1867, 2221, 2605, 2959, 3314, 3698, 4053, 4407, 4791,
        5145, 5529, 5883, 6237, 6622, 6976, 7331, 7715, 8069, 8423, 8806, 9161,
        9545, 9900, 10254, 10638, 10992, 11346, 11730, 12084, 12439, 12823, 13177, 13562,
        13916, 14270, 14654, 15008, 15362, 15746, 16101, 16485, 16839, 17194, 17578, 17932,
        18286, 18670, 19024, 19379, 19763, 20117, 20501, 20856, 21210, 21593, 21948, 22302,
        22686, 23041, 23425, 23779, 24133, 24517, 24871, 25225, 25609, 25964, 26319, 26703,
        27057, 27441, 27795, 28149, 28533, 28887
    )

    private val PACKED = intArrayOf(
        71378, 1874, 3749, 46666, 1611, 2715, 38230, 1386, 2905, 22354, 1874, 56101,
        2853, 2635, 45723, 2733, 1386, 19305, 2985, 64338, 3474, 3365, 47693, 2390,
        693, 38317, 1748, 3497, 23954, 3730, 52518, 1319, 2647, 45750, 2778, 1748,
        28329, 1865, 63123, 2707, 1323, 51803, 2413, 2922, 39764, 2980, 2889, 23187,
        2709, 62763, 1325, 2733, 46442, 3506, 3492, 32073, 3402, 72341, 2710, 1366,
        51893, 2773, 1746, 36517, 3749, 3658, 27798, 2715, 62806, 1386, 2905, 46930,
        1874, 1829, 38475, 2635, 70315, 685, 1387, 52073, 3497, 3474, 39717, 3365,
        88653, 2646, 694, 54701, 1748, 3497, 48530, 3730, 3366, 27222, 2647, 70326,
        2906, 1748, 44745, 1865, 1683, 38183, 1323, 2651, 21850, 874, 64341, 2980,
        2889, 47763, 2709, 1325, 27229, 2733, 79274, 1490, 3493, 48458, 3402, 2709,
        38189, 1366, 2741, 21930, 1746, 52901, 3749, 3658, 44182, 3227, 1370, 27349,
        2921, 96082, 1874, 2853, 54859, 2635, 1195, 42331, 1389, 2921, 23378, 3474,
        64805, 3365, 2637, 46253, 694, 1461
    )

    /** 음력 날짜 결과. month 는 1~12, leap 이 true 면 윤달. */
    data class Lunar(val year: Int, val month: Int, val day: Int, val leap: Boolean)

    private fun yearIndexOf(epochDay: Long): Int {
        if (epochDay < NEW_YEAR[0]) return -1
        var lo = 0
        var hi = NEW_YEAR.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (NEW_YEAR[mid] <= epochDay) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** 그 해의 달 순서: (달 번호, 윤달 여부, 일수) */
    private fun monthsOf(index: Int): List<Triple<Int, Boolean, Int>> {
        val p = PACKED[index]
        val leapMonth = (p shr 13) and 0xF
        val bits = p and 0x1FFF
        val out = ArrayList<Triple<Int, Boolean, Int>>(13)
        var i = 0
        for (m in 1..12) {
            out.add(Triple(m, false, if ((bits shr i) and 1 == 1) 30 else 29)); i++
            if (leapMonth == m) {
                out.add(Triple(m, true, if ((bits shr i) and 1 == 1) 30 else 29)); i++
            }
        }
        return out
    }

    /** 양력 epochDay -> 음력. 지원 범위를 벗어나면 null. */
    fun fromEpochDay(epochDay: Long): Lunar? {
        val idx = yearIndexOf(epochDay)
        if (idx < 0) return null
        var off = (epochDay - NEW_YEAR[idx]).toInt()
        for ((m, leap, len) in monthsOf(idx)) {
            if (off < len) return Lunar(BASE_YEAR + idx, m, off + 1, leap)
            off -= len
        }
        return null
    }

    /** 음력 -> 양력 epochDay. 해당 음력 날짜가 없으면 null. */
    fun toEpochDay(year: Int, month: Int, day: Int, leap: Boolean = false): Long? {
        val idx = year - BASE_YEAR
        if (idx < 0 || idx >= NEW_YEAR.size) return null
        var acc = 0
        for ((m, isLeap, len) in monthsOf(idx)) {
            if (m == month && isLeap == leap) {
                if (day < 1 || day > len) return null
                return (NEW_YEAR[idx] + acc + (day - 1)).toLong()
            }
            acc += len
        }
        return null
    }

    /** 그 음력 달의 마지막 날(29 또는 30). 없으면 null. */
    fun lastDayOfMonth(year: Int, month: Int, leap: Boolean = false): Int? {
        val idx = year - BASE_YEAR
        if (idx < 0 || idx >= NEW_YEAR.size) return null
        for ((m, isLeap, len) in monthsOf(idx)) if (m == month && isLeap == leap) return len
        return null
    }

    /** "음 9.15" 처럼 짧게. 윤달이면 "윤9.15". 범위 밖이면 빈 문자열. */
    fun shortLabel(epochDay: Long): String {
        val l = fromEpochDay(epochDay) ?: return ""
        return if (l.leap) "윤${l.month}.${l.day}" else "음${l.month}.${l.day}"
    }

    /** "음력 9월 15일" 처럼 길게. */
    fun longLabel(epochDay: Long): String {
        val l = fromEpochDay(epochDay) ?: return ""
        val yun = if (l.leap) "윤" else ""
        return "음력 $yun${l.month}월 ${l.day}일"
    }
}
