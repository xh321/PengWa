package app.pengwa.exchange

import app.pengwa.identity.Identity
import app.pengwa.identity.toBytes
import app.pengwa.identity.uuidFromBytes

/** D5 identifies a peer and its process-session advertisement, never carries card content. */
object PeerDiscovery {
    fun encode(identity: Identity, locator: Long = DiscoveryLocator.local): ByteArray =
        byteArrayOf(0xd5.toByte()) + identity.id.toBytes() + DiscoveryLocator.encode(locator)
    fun decode(bytes: ByteArray?): Identity? = bytes?.takeIf { it.size == 25 && it[0] == 0xd5.toByte() }
        ?.let { Identity(uuidFromBytes(it.copyOfRange(1, 17)), "附近娃娃", discoveryLocator = DiscoveryLocator.decode(it.copyOfRange(17, 25))) }
}
