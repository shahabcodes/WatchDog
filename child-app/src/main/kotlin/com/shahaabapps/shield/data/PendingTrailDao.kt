package com.shahaabapps.shield.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingTrailDao {

    @Insert
    suspend fun insert(point: PendingTrailPoint)

    /** Oldest buffered points first, so uploads preserve chronological order. */
    @Query("SELECT * FROM pending_trail ORDER BY timestampMillis ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<PendingTrailPoint>

    @Query("DELETE FROM pending_trail WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM pending_trail")
    suspend fun count(): Int
}
