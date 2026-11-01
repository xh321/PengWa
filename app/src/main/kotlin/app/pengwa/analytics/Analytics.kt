package app.pengwa.analytics

import java.net.URI

/** Fixed vocabulary: no caller can accidentally attach a name, ID, address or error message. */
enum class AnalyticsEvent(val eventName: String, val properties: Map<String, Any> = emptyMap()) {
    AppStarted("app_started"),
    HomeViewed("screen_view", mapOf("screen" to "home")),
    NearbyViewed("screen_view", mapOf("screen" to "nearby")),
    ContactsViewed("screen_view", mapOf("screen" to "contacts")),
    SettingsViewed("screen_view", mapOf("screen" to "settings")),
    NfcRecognized("peer_recognized", mapOf("source" to "nfc")),
    QrRecognized("peer_recognized", mapOf("source" to "qr")),
    ExchangeStarted("ble_exchange_started", mapOf("direction" to "outgoing")),
    ExchangeSucceeded("ble_exchange_finished", mapOf("direction" to "outgoing", "result" to "success")),
    ExchangeFailed("ble_exchange_finished", mapOf("direction" to "outgoing", "result" to "failure")),
    CardReceived("ble_card_received"),
}

/** Invalid/missing config fails closed, including self-hosted keys without an HTTPS origin. */
data class AnalyticsConfig(val appKey: String, val host: String = "") {
    val valid: Boolean get() {
        val match = Regex("A-(US|EU|SH)-[A-Za-z0-9]+").matchEntire(appKey) ?: return false
        if (match.groupValues[1] != "SH") return host.isEmpty()
        return runCatching {
            val uri = URI(host)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null && uri.path.orEmpty() in listOf("", "/")
        }.getOrDefault(false)
    }
}

/** Checks the latest consent before each submission. Disabled events are never replayed. */
class Analytics(
    private val configured: Boolean,
    private val consent: () -> Boolean,
    private val submit: (AnalyticsEvent) -> Unit,
) {
    @Synchronized
    fun track(event: AnalyticsEvent) {
        if (configured && consent()) runCatching { submit(event) }
    }

}
