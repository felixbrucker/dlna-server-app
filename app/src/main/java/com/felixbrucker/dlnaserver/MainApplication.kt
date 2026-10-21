package com.felixbrucker.dlnaserver

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import com.felixbrucker.dlnaserver.core.logging.AppLogTree
import com.felixbrucker.dlnaserver.core.logging.LogRepository
import timber.log.Timber

@HiltAndroidApp
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LogRepository.init(this)
        Timber.plant(AppLogTree())
        Timber.i("Application initialized with Timber logging")
    }
}
