package app.pengwa.exchange

import app.pengwa.identity.Identity
import app.pengwa.identity.toBytes
import app.pengwa.identity.uuidFromBytes

/** Version D2 is deliberately incompatible with old card exchange. Contains only a peer locator. */
object PeerDiscovery {
    fun encode(identity: Identity): ByteArray = byteArrayOf(0xd2.toByte()) + identity.id.toBytes()
    fun decode(bytes: ByteArray?): Identity? = bytes?.takeIf { it.size == 17 && it[0] == 0xd2.toByte() }
        ?.let { Identity(uuidFromBytes(it.copyOfRange(1, 17)), "附近娃娃") }
}
