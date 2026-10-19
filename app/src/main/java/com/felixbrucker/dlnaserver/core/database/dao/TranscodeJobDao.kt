package com.felixbrucker.dlnaserver.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.felixbrucker.dlnaserver.core.database.entity.TranscodeJobEntity
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJobStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscodeJobDao {
    @Query("SELECT * FROM transcode_jobs ORDER BY createdAt ASC")
    fun getAllJobs(): Flow<List<TranscodeJobEntity>>

    @Query("SELECT * FROM transcode_jobs ORDER BY createdAt ASC")
    suspend fun getAllJobsSync(): List<TranscodeJobEntity>

    @Query("SELECT * FROM transcode_jobs WHERE id = :id LIMIT 1")
    suspend fun getJobById(id: String): TranscodeJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: TranscodeJobEntity)

    @Update
    suspend fun updateJob(job: TranscodeJobEntity)

    @Query("UPDATE transcode_jobs SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateJobStatus(id: String, status: TranscodeJobStatus, errorMessage: String? = null)

    @Delete
    suspend fun deleteJob(job: TranscodeJobEntity)

    @Query("DELETE FROM transcode_jobs WHERE id = :id")
    suspend fun deleteJobById(id: String)
}
