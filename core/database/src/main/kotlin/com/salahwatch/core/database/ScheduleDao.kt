package com.salahwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
public interface ScheduleDao {

    /**
     * Days covering an inclusive epoch-day range.
     *
     * Callers ask for a range rather than a single day because "the next prayer" routinely lives on
     * tomorrow — after Isha it always does — so a one-day query would be wrong most evenings.
     */
    @Query("SELECT * FROM day_schedule WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay")
    public suspend fun range(fromDay: Long, toDay: Long): List<ScheduleEntity>

    /**
     * Emits whenever the table changes, for UI that should refresh when a sync lands.
     *
     * Deliberately a count and deliberately unbounded by date. Room invalidates per *table*, so a
     * ranged query would emit no more often — it would only cost a list of entities to build and
     * discard, and it would need a date, which would then be captured for the lifetime of whatever
     * held the flow and go stale at midnight. The number itself is ignored; the emission is the
     * signal, and Room re-emits on every write even when the count is unchanged.
     */
    @Query("SELECT COUNT(*) FROM day_schedule")
    public fun observeChanges(): Flow<Int>

    /** Replaces on conflict, so an authoritative fetch overwrites a local estimate for the same day. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun upsert(entities: List<ScheduleEntity>)

    @Query("DELETE FROM day_schedule")
    public suspend fun clear()

    /** Drops days before [beforeDay], so a year-long cache does not grow without bound. */
    @Query("DELETE FROM day_schedule WHERE epochDay < :beforeDay")
    public suspend fun pruneBefore(beforeDay: Long)

    @Query("SELECT COUNT(*) FROM day_schedule")
    public suspend fun count(): Int
}
