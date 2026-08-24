package com.safevault.app.data.db

import androidx.room.TypeConverter

/**
 * Stores the tag list as a single delimited string.
 * Tags are normalized (trimmed, non-empty) upstream in the use-case layer,
 * so a simple newline delimiter is safe and avoids a JSON dependency here.
 */
class Converters {

    @TypeConverter
    fun fromTags(tags: List<String>): String = tags.joinToString(DELIMITER)

    @TypeConverter
    fun toTags(value: String): List<String> =
        if (value.isEmpty()) emptyList()
        else value.split(DELIMITER).filter { it.isNotEmpty() }

    private companion object {
        const val DELIMITER = "\n"
    }
}
