package com.felixbrucker.dlnaserver.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.felixbrucker.dlnaserver.core.database.entity.SharedDirectoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedDirectoryDao {
    @Query("SELECT * FROM shared_directories ORDER BY displayName COLLATE NOCASE ASC, path COLLATE NOCASE ASC")
    fun getAllDirectories(): Flow<List<SharedDirectoryEntity>>

    @Query("SELECT * FROM shared_directories WHERE isEnabled = 1 ORDER BY displayName COLLATE NOCASE ASC, path COLLATE NOCASE ASC")
    fun getEnabledDirectories(): Flow<List<SharedDirectoryEntity>>

    @Query("SELECT * FROM shared_directories WHERE isEnabled = 1 ORDER BY displayName COLLATE NOCASE ASC, path COLLATE NOCASE ASC")
    suspend fun getEnabledDirectoriesSync(): List<SharedDirectoryEntity>

    @Query("SELECT * FROM shared_directories WHERE path = :path LIMIT 1")
    suspend fun getDirectoryByPath(path: String): SharedDirectoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectory(directory: SharedDirectoryEntity): Long

    @Update
    suspend fun updateDirectory(directory: SharedDirectoryEntity)

    @Delete
    suspend fun deleteDirectory(directory: SharedDirectoryEntity)

    @Query("DELETE FROM shared_directories WHERE id = :id")
    suspend fun deleteDirectoryById(id: Long)
}
