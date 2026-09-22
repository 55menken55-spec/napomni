package ru.napomni.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import ru.napomni.app.data.model.Category
import ru.napomni.app.data.model.Note
import ru.napomni.app.data.model.NoteBlock
import ru.napomni.app.data.model.Occurrence
import ru.napomni.app.data.model.Reminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter fun toLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun fromLocalTimeList(value: List<LocalTime>): String =
        value.joinToString("|") { it.toString() }

    @TypeConverter fun toLocalTimeList(value: String): List<LocalTime> =
        if (value.isBlank()) emptyList() else value.split("|").map(LocalTime::parse)

    @TypeConverter fun fromDayOfWeekList(value: List<DayOfWeek>): String =
        value.joinToString(",") { it.value.toString() }

    @TypeConverter fun toDayOfWeekList(value: String): List<DayOfWeek> =
        if (value.isBlank()) emptyList() else value.split(",").map { DayOfWeek.of(it.toInt()) }
}

@Database(
    entities = [
        Reminder::class,
        Note::class,
        NoteBlock::class,
        Category::class,
        Occurrence::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class NapomniDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun occurrenceDao(): OccurrenceDao

    companion object {
        @Volatile
        private var INSTANCE: NapomniDatabase? = null

        fun get(context: Context): NapomniDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NapomniDatabase::class.java,
                    "napomni.db",
                )
                    // v2: уникальный индекс occurrences. Пока в разработке — при смене
                    // схемы данные очищаются (для релиза добавим миграции).
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
