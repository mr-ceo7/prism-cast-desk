package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.StreamSettings
import com.example.data.model.SessionRecord
import com.example.data.model.MotionLog
import com.example.data.model.TodoItem
import com.example.data.model.NotepadNote
import com.example.data.model.KpiCard

@Database(
    entities = [
        StreamSettings::class,
        SessionRecord::class,
        MotionLog::class,
        TodoItem::class,
        NotepadNote::class,
        KpiCard::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): StreamSettingsDao
    abstract fun sessionRecordDao(): SessionRecordDao
    abstract fun motionLogDao(): MotionLogDao
    abstract fun todoDao(): TodoDao
    abstract fun notepadDao(): NotepadDao
    abstract fun kpiCardDao(): KpiCardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screen_stream_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
