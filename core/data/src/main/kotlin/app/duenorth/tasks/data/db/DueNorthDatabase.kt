package app.duenorth.tasks.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class,
        TaskListEntity::class,
        TaskEntity::class,
        StepEntity::class,
        PendingOperationEntity::class,
        SyncLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class DueNorthDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun taskListDao(): TaskListDao

    abstract fun taskDao(): TaskDao

    abstract fun stepDao(): StepDao

    abstract fun pendingOperationDao(): PendingOperationDao

    abstract fun syncLogDao(): SyncLogDao

    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "duenorth.db"

        fun build(context: Context): DueNorthDatabase = Room
            .databaseBuilder(context, DueNorthDatabase::class.java, NAME)
            .build()
    }
}
