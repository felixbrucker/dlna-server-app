package com.felixbrucker.dlnaserver.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.felixbrucker.dlnaserver.core.database.dao.SharedDirectoryDao
import com.felixbrucker.dlnaserver.core.database.dao.TranscodeJobDao
import com.felixbrucker.dlnaserver.core.database.entity.SharedDirectoryEntity
import com.felixbrucker.dlnaserver.core.database.entity.TranscodeJobEntity
import com.felixbrucker.dlnaserver.core.database.util.Converters

@Database(
    entities = [SharedDirectoryEntity::class, TranscodeJobEntity::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ],
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sharedDirectoryDao(): SharedDirectoryDao
    abstract fun transcodeJobDao(): TranscodeJobDao
}
