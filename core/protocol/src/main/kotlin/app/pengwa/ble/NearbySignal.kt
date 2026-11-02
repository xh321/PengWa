package app.pengwa.ble

/** RSSI describes received signal strength, not calibrated physical distance. */
enum class NearbySignal(val bars: Int) {
    Strong(3), Medium(2), Weak(1), Unknown(0);

    companion object {
        // ScanResult.getRssi() defines the valid range as [-127, 126].
        fun fromRssi(rssi: Int): NearbySignal = when {
            rssi !in -127..126 -> Unknown
            rssi >= -60 -> Strong
            rssi >= -75 -> Medium
            else -> Weak
        }
    }
}
