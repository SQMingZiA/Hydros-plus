package org.cqutosp.hydros

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.cqutosp.hydros.data.ble.DevicePresence
import org.cqutosp.hydros.data.ble.NearbyAdvertisement
import org.cqutosp.hydros.data.ble.NearbyScanner
import org.cqutosp.hydros.data.ble.NoNearbyScanner
import org.cqutosp.hydros.data.ble.matchesSeen
import org.cqutosp.hydros.ui.state.DeviceCategory
import org.cqutosp.hydros.ui.state.DeviceUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePresenceTest {
    private fun device(connectMac: String, ledgerMac: String) = DeviceUi(
        id = "test-device", name = "测试水表", location = "宿舍", category = DeviceCategory.SHOWER,
        connectMac = connectMac, ledgerMac = ledgerMac, isBle = true,
    )

    @Test fun collectsBroadcastAddressesWithinTheScanWindow() = runTest {
        val scanner = NearbyScanner {
            flow {
                emit(NearbyAdvertisement("KLCXKJ-Water", "aa:bb:cc:dd:ee:ff", -60))
                awaitCancellation()
            }
        }
        assertEquals(setOf("AABBCCDDEEFF"), DevicePresence(scanner).seenMacs(50))
    }

    @Test fun unavailableScannerIsUnknownInsteadOfOffline() = runTest {
        assertNull(DevicePresence(NoNearbyScanner).seenMacs(50))
    }

    @Test fun scannerFailureIsUnknown() = runTest {
        val scanner = NearbyScanner { flow<NearbyAdvertisement> { error("scan failed") } }
        assertNull(DevicePresence(scanner).seenMacs(50))
    }

    @Test fun matchesEitherConnectionOrLedgerAddress() {
        val device = device(connectMac = "AA:BB:CC:DD:EE:FF", ledgerMac = "11:22:33:44:55:66")
        assertTrue(device.matchesSeen(setOf("AABBCCDDEEFF")))
        assertTrue(device.matchesSeen(setOf("112233445566")))
        assertFalse(device.matchesSeen(setOf("999999999999")))
    }

    @Test fun unparsableAddressIsTreatedAsUnknown() {
        assertTrue(device(connectMac = "", ledgerMac = "").matchesSeen(emptySet()))
    }
}
