package dev.dhun.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectivityMonitorPolicyTest {

    @Test
    fun `online requires both internet route and Android validation`() {
        assertFalse(isInternetValidated(hasInternetCapability = false, hasValidatedCapability = false))
        assertFalse(isInternetValidated(hasInternetCapability = false, hasValidatedCapability = true))
        assertFalse(isInternetValidated(hasInternetCapability = true, hasValidatedCapability = false))
        assertTrue(isInternetValidated(hasInternetCapability = true, hasValidatedCapability = true))
    }
}
