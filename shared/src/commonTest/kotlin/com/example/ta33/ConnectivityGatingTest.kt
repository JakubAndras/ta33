package com.example.ta33

import com.example.ta33.domain.model.NetworkPreference
import com.example.ta33.domain.model.NetworkType
import com.example.ta33.domain.usecase.PrepareOfflinePackageUseCase.Companion.networkAllows
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectivityGatingTest {

    @Test
    fun wifiOnly_blocksCellular_allowsWifi() {
        assertFalse(networkAllows(NetworkPreference.WIFI_ONLY, NetworkType.CELLULAR))
        assertTrue(networkAllows(NetworkPreference.WIFI_ONLY, NetworkType.WIFI))
    }

    @Test
    fun wifiAndCellular_allowsBoth() {
        assertTrue(networkAllows(NetworkPreference.WIFI_AND_CELLULAR, NetworkType.CELLULAR))
        assertTrue(networkAllows(NetworkPreference.WIFI_AND_CELLULAR, NetworkType.WIFI))
    }

    @Test
    fun none_alwaysBlocked() {
        assertFalse(networkAllows(NetworkPreference.WIFI_ONLY, NetworkType.NONE))
        assertFalse(networkAllows(NetworkPreference.WIFI_AND_CELLULAR, NetworkType.NONE))
    }

    @Test
    fun other_blocked() {
        assertFalse(networkAllows(NetworkPreference.WIFI_ONLY, NetworkType.OTHER))
        assertFalse(networkAllows(NetworkPreference.WIFI_AND_CELLULAR, NetworkType.OTHER))
    }
}
