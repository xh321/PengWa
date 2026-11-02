package app.pengwa.ble

import kotlin.test.Test
import kotlin.test.assertEquals

class NearbySignalTest {
    @Test fun `signal bands include their boundaries`() {
        for (rssi in -60..126) assertEquals(NearbySignal.Strong, NearbySignal.fromRssi(rssi))
        for (rssi in -75..-61) assertEquals(NearbySignal.Medium, NearbySignal.fromRssi(rssi))
        for (rssi in -127..-76) assertEquals(NearbySignal.Weak, NearbySignal.fromRssi(rssi))
    }

    @Test fun `unavailable and invalid signals never look nearby`() {
        listOf(127, -128, Int.MIN_VALUE, Int.MAX_VALUE).forEach {
            assertEquals(NearbySignal.Unknown, NearbySignal.fromRssi(it))
            assertEquals(0, NearbySignal.fromRssi(it).bars)
        }
    }
}
