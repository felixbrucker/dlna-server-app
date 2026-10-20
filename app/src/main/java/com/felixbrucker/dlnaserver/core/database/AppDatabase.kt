package com.felixbrucker.dlnaserver.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import com.felixbrucker.dlnaserver.core.database.dao.SharedDirectoryDao
import com.felixbrucker.dlnaserver.core.database.dao.TranscodeJobDao
import com.felixbrucker.dlnaserver.core.database.entity.SharedDirectoryEntity
import com.felixbrucker.dlnaserver.core.database.entity.TranscodeJobEntity
import com.felixbrucker.dlnaserver.core.database.util.Converters

@Database(
    entities = [SharedDirectoryEntity::class, TranscodeJobEntity::class],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = AppDatabase.Migration3To4::class),
        AutoMigration(from = 4, to = 5),
    ],
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sharedDirectoryDao(): SharedDirectoryDao
    abstract fun transcodeJobDao(): TranscodeJobDao

    @RenameColumn(
        tableName = "transcode_jobs",
        fromColumnName = "codecChange",
        toColumnName = "videoCodecChange"
    )
    class Migration3To4 : AutoMigrationSpec
}
