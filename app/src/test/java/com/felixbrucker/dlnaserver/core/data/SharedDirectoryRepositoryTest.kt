package com.felixbrucker.dlnaserver.core.data

import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.database.dao.SharedDirectoryDao
import com.felixbrucker.dlnaserver.core.database.entity.SharedDirectoryEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedDirectoryRepositoryTest {

    @Test
    fun testAllDirectoriesFlow() = runTest {
        val daoMock = mockk<SharedDirectoryDao>(relaxed = true)
        val entities = listOf(
            SharedDirectoryEntity(id = 1, path = "/tmp", displayName = "Temp")
        )
        coEvery { daoMock.getAllDirectories() } returns flowOf(entities)
        val repository = SharedDirectoryRepository(daoMock)

        val result = repository.allDirectories.first()

        assertEquals(1, result.size)
        assertEquals("Temp", result[0].displayName)
    }

    @Test
    fun testAddDirectoryWithValidFolder() = runTest {
        val daoMock = mockk<SharedDirectoryDao>(relaxed = true)
        val repository = SharedDirectoryRepository(daoMock)
        val tempDir = File.createTempFile("dlna_test", "").apply {
            delete()
            mkdir()
        }
        coEvery { daoMock.getDirectoryByPath(any()) } returns null
        coEvery { daoMock.insertDirectory(any()) } returns 1L

        val added = repository.addDirectory(tempDir.absolutePath, "Test Shared")

        assertTrue(added)
        coVerify { daoMock.insertDirectory(any()) }
        tempDir.delete()
    }

    @Test
    fun testDeleteDirectory() = runTest {
        val daoMock = mockk<SharedDirectoryDao>(relaxed = true)
        val repository = SharedDirectoryRepository(daoMock)
        coEvery { daoMock.deleteDirectoryById(1L) } returns Unit

        repository.deleteDirectory(1L)

        coVerify { daoMock.deleteDirectoryById(1L) }
    }
}
