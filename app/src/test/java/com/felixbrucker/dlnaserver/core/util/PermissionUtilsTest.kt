package com.felixbrucker.dlnaserver.core.util

import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionUtilsTest {

    @Test
    fun testGetRequiredPermissionsContainsReadExternalStorage() {
        val permissions = PermissionUtils.getRequiredPermissions()

        assertTrue(permissions.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE))
    }
}
