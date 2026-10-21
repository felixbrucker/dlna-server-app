package com.felixbrucker.dlnaserver.core.extensions

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController

internal fun NavController.popBackStackSafely(): Boolean {
    val currentEntry = currentBackStackEntry ?: return false
    if (currentEntry.lifecycle.currentState != Lifecycle.State.RESUMED) {
        return false
    }
    if (previousBackStackEntry == null) {
        return false
    }
    return popBackStack()
}
