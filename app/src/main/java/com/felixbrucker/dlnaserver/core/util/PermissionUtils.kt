package com.felixbrucker.dlnaserver.core.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

object PermissionUtils {

    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.toTypedArray()
    }

    fun hasStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasVideo = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_VIDEO,
                ) == PackageManager.PERMISSION_GRANTED
                val hasAudio = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                val hasImages = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_IMAGES,
                ) == PackageManager.PERMISSION_GRANTED
                return hasVideo || hasAudio || hasImages
            }
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) || (ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED)
    }

    fun getMissingPermissions(context: Context): Array<String> {
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (!hasStoragePermission(context)) {
            for (permission in getRequiredPermissions()) {
                if ((permission != Manifest.permission.POST_NOTIFICATIONS) &&
                    (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
                ) {
                    missing.add(permission)
                }
            }
        }
        return missing.toTypedArray()
    }

    fun hasAllPermissions(context: Context): Boolean {
        return hasStoragePermission(context) && hasNotificationPermission(context)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }
}
