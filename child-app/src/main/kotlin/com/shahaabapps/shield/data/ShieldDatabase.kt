package com.shahaabapps.shield.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PendingTrailPoint::class], version = 1, exportSchema = false)
abstract class ShieldDatabase : RoomDatabase() {
    abstract fun pendingTrailDao(): PendingTrailDao
}
