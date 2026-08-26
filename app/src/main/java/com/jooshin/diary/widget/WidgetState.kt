package com.jooshin.diary.widget

import android.content.Context

/** 위젯별 기준 날짜(anchor) 저장. 월=그 달 1일, 주=그 주 일요일, 일=해당 날짜 (epochDay). */
object WidgetState {
    private const val FILE = "widget_state"

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getAnchor(c: Context, id: Int, default: Long): Long =
        p(c).getLong("anchor_$id", default)

    fun setAnchor(c: Context, id: Int, value: Long) {
        p(c).edit().putLong("anchor_$id", value).apply()
    }

    fun clear(c: Context, id: Int) {
        p(c).edit().remove("anchor_$id").apply()
    }
}
