package app.pengwa.exchange

import app.pengwa.identity.Identity
import java.util.UUID
import kotlin.test.*
import org.junit.Test

class DiscoveryLocatorTest {
    @Test fun `discovery binds UUID and random session locator without card content`() {
        val me = Identity(UUID.randomUUID(), "Alice")
        for (locator in listOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            val bytes = PeerDiscovery.encode(me, locator)
            assertEquals(25, bytes.size)
            val peer = assertNotNull(PeerDiscovery.decode(bytes))
            assertEquals(me.id, peer.id)
            assertEquals(locator, peer.discoveryLocator)
            assertFalse(peer.cardComplete)
            assertEquals(locator, DiscoveryLocator.decode(DiscoveryLocator.encode(locator)))
            for (old in 0xd2..0xd5) assertNull(PeerDiscovery.decode(bytes.copyOf().also { it[0] = old.toByte() }))
            assertNull(PeerDiscovery.decode(bytes.dropLast(1).toByteArray()))
            assertNull(PeerDiscovery.decode(bytes + byteArrayOf(0)))
        }
        assertNull(DiscoveryLocator.decode(ByteArray(7)))
        assertNull(DiscoveryLocator.decode(null))
    }
}
