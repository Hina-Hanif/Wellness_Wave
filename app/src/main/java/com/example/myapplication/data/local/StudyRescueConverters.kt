package com.example.myapplication.data.local

import androidx.room.TypeConverter

class StudyRescueConverters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        return list?.filter { it.isNotBlank() }?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        return data.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
