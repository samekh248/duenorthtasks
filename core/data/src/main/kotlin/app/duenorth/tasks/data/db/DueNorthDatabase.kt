package app.duenorth.tasks.data.db

import android.content.Context
import androidx.room.AutoMigration
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
        SyncLogEntity::class,
        TemplateListEntity::class,
        TemplateTaskEntity::class,
        TemplateStepEntity::class
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // Spec 002: list sharing flags and task assignment, all with defaults.
        AutoMigration(from = 1, to = 2),
        // Spec 004: three new template tables.
        AutoMigration(from = 2, to = 3),
        // "clear completed": when it last ran in each list.
        AutoMigration(from = 3, to = 4)
    ]
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

    abstract fun templateDao(): TemplateDao

    companion object {
        const val NAME = "duenorth.db"

        fun build(context: Context): DueNorthDatabase = Room
            .databaseBuilder(context, DueNorthDatabase::class.java, NAME)
            .build()
    }
}
