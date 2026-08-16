package com.salahwatch.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScheduleEntity::class], version = 1, exportSchema = true)
internal abstract class PrayerDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var instance: PrayerDatabase? = null

        /**
         * The process-wide database.
         *
         * Complication services are short-lived and may be created several times in quick
         * succession as a watch face binds its slots, so a shared instance matters more here than in
         * a typical app — building a new one per request would open the file repeatedly on a code
         * path that is meant to be cheap.
         *
         * `fallbackToDestructiveMigration` is safe for this schema: every row is a cache of data
         * that can be recomputed offline or refetched, so losing it costs a recalculation, never
         * user data.
         */
        fun get(context: Context): PrayerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrayerDatabase::class.java,
                    "prayer_schedule.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
