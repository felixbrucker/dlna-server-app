package com.felixbrucker.dlnaserver.core.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUtilsTest {

    @Test
    fun testGetAvailableNetworkInterfacesDoesNotCrash() {
        val interfaces = NetworkUtils.getAvailableNetworkInterfaces()

        assertNotNull(interfaces)
    }

    @Test
    fun testGetNetworkInterfaceByNameAutoReturnsOrNull() {
        val netIf = NetworkUtils.getNetworkInterfaceByName("auto")

        assertTrue(netIf == null || netIf.ipAddress.isNotEmpty())
    }
}
