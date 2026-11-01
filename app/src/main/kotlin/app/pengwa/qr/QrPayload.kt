package app.pengwa.qr

import app.pengwa.exchange.PeerDiscovery
import app.pengwa.identity.Identity
import java.util.Base64

/** A QR identifies a BLE peer; it never carries or imports the user's card. */
object QrPayload {
    const val PREFIX = "pengwa://p/"
    private const val MAX_LENGTH = 200

    fun encode(identity: Identity): String =
        PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(PeerDiscovery.encode(identity))

    fun decode(text: String): Identity? {
        val trimmed = text.trim()
        if (trimmed.length > MAX_LENGTH) return null
        if (!trimmed.startsWith(PREFIX)) return null
        val bytes = runCatching { Base64.getUrlDecoder().decode(trimmed.removePrefix(PREFIX)) }.getOrNull() ?: return null
        return PeerDiscovery.decode(bytes)
    }
}
