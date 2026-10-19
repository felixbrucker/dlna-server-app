package com.felixbrucker.dlnaserver.core.database.di

import android.content.Context
import androidx.room.Room
import com.felixbrucker.dlnaserver.core.database.AppDatabase
import com.felixbrucker.dlnaserver.core.database.dao.SharedDirectoryDao
import com.felixbrucker.dlnaserver.core.database.dao.TranscodeJobDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "dlna_server.db"
        ).build()
    }

    @Provides
    fun provideSharedDirectoryDao(
        database: AppDatabase
    ): SharedDirectoryDao {
        return database.sharedDirectoryDao()
    }

    @Provides
    fun provideTranscodeJobDao(
        database: AppDatabase
    ): TranscodeJobDao {
        return database.transcodeJobDao()
    }
}
