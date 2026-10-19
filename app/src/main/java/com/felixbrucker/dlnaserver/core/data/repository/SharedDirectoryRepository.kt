package com.felixbrucker.dlnaserver.core.data.repository

import com.felixbrucker.dlnaserver.core.database.dao.SharedDirectoryDao
import com.felixbrucker.dlnaserver.core.database.entity.SharedDirectoryEntity
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedDirectoryRepository @Inject constructor(
    private val sharedDirectoryDao: SharedDirectoryDao
) {

    val allDirectories: Flow<List<SharedDirectory>> =
        sharedDirectoryDao.getAllDirectories().map { list ->
            list.map { it.toDomain() }
        }

    suspend fun getEnabledDirectoriesSync(): List<SharedDirectory> {
        return sharedDirectoryDao.getEnabledDirectoriesSync().map { it.toDomain() }
    }

    suspend fun addDirectory(path: String, displayName: String? = null): Boolean {
        val file = File(path)
        if (!file.exists() || !file.isDirectory) {
            return false
        }
        val canonicalPath = file.canonicalPath
        val name = if (displayName.isNullOrBlank()) file.name.ifEmpty { canonicalPath } else displayName

        val existing = sharedDirectoryDao.getDirectoryByPath(canonicalPath)
        if (existing != null) {
            sharedDirectoryDao.updateDirectory(existing.copy(isEnabled = true))
        } else {
            val entity = SharedDirectoryEntity(
                path = canonicalPath,
                displayName = name,
                isEnabled = true
            )
            sharedDirectoryDao.insertDirectory(entity)
        }
        return true
    }

    suspend fun toggleDirectory(directory: SharedDirectory) {
        val existing = sharedDirectoryDao.getDirectoryByPath(directory.path)
        if (existing != null) {
            sharedDirectoryDao.updateDirectory(existing.copy(isEnabled = !existing.isEnabled))
        }
    }

    suspend fun deleteDirectory(id: Long) {
        sharedDirectoryDao.deleteDirectoryById(id)
    }

    suspend fun removeDirectory(id: Long) {
        deleteDirectory(id)
    }

    private fun SharedDirectoryEntity.toDomain() = SharedDirectory(
        id = id,
        path = path,
        displayName = displayName,
        isEnabled = isEnabled,
        addedTimestamp = addedTimestamp
    )
}
