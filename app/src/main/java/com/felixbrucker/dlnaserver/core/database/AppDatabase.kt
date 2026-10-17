package com.felixbrucker.dlnaserver.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.felixbrucker.dlnaserver.core.database.dao.SharedDirectoryDao
import com.felixbrucker.dlnaserver.core.database.entity.SharedDirectoryEntity

@Database(
    entities = [SharedDirectoryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sharedDirectoryDao(): SharedDirectoryDao
}
