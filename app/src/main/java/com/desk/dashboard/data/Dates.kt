package com.desk.dashboard.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

enum class Category(val label: String) {
    EXAM("Exam"), DEADLINE("Deadline"), PERSONAL("Personal"), TRIP("Trip"), OTHER("Other")
}

@Entity(tableName = "upcoming_dates")
data class UpcomingDate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val category: Category = Category.OTHER,
    val note: String? = null,
) {
    companion object {
        const val TITLE_MAX = 40
        const val NOTE_MAX = 60
    }
}

class Converters {
    // ISO strings sort lexically in date order, so ORDER BY date works on the raw column.
    @TypeConverter fun dateToString(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun stringToDate(s: String?): LocalDate? = s?.let(LocalDate::parse)
    @TypeConverter fun timeToString(t: LocalTime?): String? = t?.toString()
    @TypeConverter fun stringToTime(s: String?): LocalTime? = s?.let(LocalTime::parse)
}

@Dao
interface DatesDao {
    /** Everything from [today] on, soonest first; untimed entries sort before timed ones on a day. */
    @Query("SELECT * FROM upcoming_dates WHERE date >= :today ORDER BY date, time IS NOT NULL, time, id")
    fun upcoming(today: LocalDate): Flow<List<UpcomingDate>>

    @Upsert
    suspend fun upsert(d: UpcomingDate)

    @Delete
    suspend fun delete(d: UpcomingDate)

    /** Entries disappear the day after their date; this also keeps the settings list clean. */
    @Query("DELETE FROM upcoming_dates WHERE date < :today")
    suspend fun purgeBefore(today: LocalDate)
}

@Database(entities = [UpcomingDate::class], version = 1)
@TypeConverters(Converters::class)
abstract class DashDb : RoomDatabase() {
    abstract fun dates(): DatesDao

    companion object {
        @Volatile private var instance: DashDb? = null
        fun get(context: Context): DashDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DashDb::class.java, "dash.db")
                .build().also { instance = it }
        }
    }
}
