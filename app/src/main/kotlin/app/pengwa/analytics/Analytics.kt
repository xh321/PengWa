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
    NfcDisplayModeSelected("exchange_mode_selected", mapOf("mode" to "nfc_display")),
    NfcReadModeSelected("exchange_mode_selected", mapOf("mode" to "nfc_read")),
    QrScanModeSelected("exchange_mode_selected", mapOf("mode" to "qr_scan")),
    IncomingPrompted("ble_incoming_prompted"),
    IncomingAccepted("ble_incoming_decided", mapOf("decision" to "accept", "mechanism" to "manual")),
    IncomingDeclined("ble_incoming_decided", mapOf("decision" to "decline", "mechanism" to "manual")),
    IncomingAutoAccepted("ble_incoming_decided", mapOf("decision" to "accept", "mechanism" to "nfc_auto")),
    IncomingCooldownDeclined("ble_incoming_decided", mapOf("decision" to "decline", "mechanism" to "cooldown")),
    IncomingCapacityDeclined("ble_incoming_decided", mapOf("decision" to "decline", "mechanism" to "capacity")),
    IncomingExpired("ble_incoming_decided", mapOf("decision" to "expired", "mechanism" to "timeout")),
    NfcDiscoveryRetried("peer_discovery_retried", mapOf("source" to "nfc")),
    QrDiscoveryRetried("peer_discovery_retried", mapOf("source" to "qr")),
    NfcDiscoveryTimedOut("peer_discovery_timed_out", mapOf("source" to "nfc")),
    QrDiscoveryTimedOut("peer_discovery_timed_out", mapOf("source" to "qr")),
    CardCreated("card_created"),
    CardEditStarted("card_edit_started"),
    CardEditorPreviewOpened("card_editor_preview_opened"),
    CardEditSaved("card_edit_finished", mapOf("result" to "success")),
    CardEditCancelled("card_edit_finished", mapOf("result" to "cancelled")),
    OwnCardViewed("card_viewed", mapOf("card" to "own")),
    ContactCardViewed("card_viewed", mapOf("card" to "contact")),
    QrScanOpened("qr_scan_opened"),
    QrScanCancelled("qr_scan_finished", mapOf("result" to "cancelled")),
    QrScanInvalid("qr_scan_finished", mapOf("result" to "invalid")),
    QrScanSelf("qr_scan_finished", mapOf("result" to "self")),
    QrScanPeer("qr_scan_finished", mapOf("result" to "peer")),
    PhotoCaptureStarted("photo_capture_started"),
    PhotoCaptureSaved("photo_capture_finished", mapOf("result" to "success")),
    PhotoCaptureCancelled("photo_capture_finished", mapOf("result" to "cancelled")),
    PhotoCaptureFailed("photo_capture_finished", mapOf("result" to "failure")),
    BackgroundBroadcastRequestedOn("preference_requested", mapOf("setting" to "background_broadcast", "value" to "on")),
    BackgroundBroadcastRequestedOff("preference_requested", mapOf("setting" to "background_broadcast", "value" to "off")),
    NotificationRequestedOn("preference_requested", mapOf("setting" to "ongoing_notification", "value" to "on")),
    NotificationRequestedOff("preference_requested", mapOf("setting" to "ongoing_notification", "value" to "off")),
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

/** Serializes consent and submission. Disabled events are dropped, never replayed on opt-in. */
class Analytics(
    private val configured: Boolean,
    private val consent: () -> Boolean,
    private val submit: (AnalyticsEvent) -> Unit,
) {
    @Synchronized
    fun track(event: AnalyticsEvent) {
        if (configured && consent()) runCatching { submit(event) }
    }

    @Synchronized
    fun changeConsent(change: () -> Unit) = change()
}
