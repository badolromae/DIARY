package com.jooshin.diary.data

import androidx.room.TypeConverter

/** Room 은 List 를 직접 저장할 수 없어, 잘 쓰이지 않는 구분자(Unit Separator, U+001F)로 이어붙여 저장한다. */
class Converters {
    @TypeConverter
    fun fromList(list: List<String>?): String =
        list?.filter { it.isNotEmpty() }?.joinToString(SEP) ?: ""

    @TypeConverter
    fun toList(data: String?): List<String> =
        if (data.isNullOrEmpty()) emptyList() else data.split(SEP).filter { it.isNotEmpty() }

    companion object {
        private const val SEP = "\u001F"
    }
}
