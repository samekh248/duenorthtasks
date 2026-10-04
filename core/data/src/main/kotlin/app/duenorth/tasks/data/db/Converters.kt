package app.duenorth.tasks.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /** Dates are stored as epoch days so "due on or before today" is a plain number comparison. */
    @TypeConverter
    fun dateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun fieldsToJson(value: Set<String>): String = encodeFields(value)

    @TypeConverter
    fun jsonToFields(value: String): Set<String> = decodeFields(value)

    companion object {
        private val NAME = Regex("^[A-Za-z]+$")

        /** Field names are plain identifiers, so a JSON array of strings needs no library. */
        fun encodeFields(fields: Set<String>): String {
            require(fields.all { NAME.matches(it) }) { "Field names must be letters only: $fields" }
            return fields.sorted().joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
        }

        fun decodeFields(json: String): Set<String> = json
            .removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
