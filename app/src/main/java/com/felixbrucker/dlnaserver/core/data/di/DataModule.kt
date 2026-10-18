package com.felixbrucker.dlnaserver.core.data.di

import com.felixbrucker.dlnaserver.core.data.repository.DlnaRepository
import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.database.dao.SharedDirectoryDao
import com.felixbrucker.dlnaserver.core.datastore.AppSettingsRepository
import com.felixbrucker.dlnaserver.core.dlna.DlnaServerEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSharedDirectoryRepository(
        sharedDirectoryDao: SharedDirectoryDao
    ): SharedDirectoryRepository {
        return SharedDirectoryRepository(sharedDirectoryDao)
    }

    @Provides
    @Singleton
    fun provideDlnaRepository(
        dlnaServerEngine: DlnaServerEngine,
        sharedDirectoryRepository: SharedDirectoryRepository,
        appSettingsRepository: AppSettingsRepository
    ): DlnaRepository {
        return DlnaRepository(dlnaServerEngine, sharedDirectoryRepository, appSettingsRepository)
    }
}
