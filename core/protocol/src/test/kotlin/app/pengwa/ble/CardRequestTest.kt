package app.pengwa.ble

import app.pengwa.identity.*
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.test.*
import org.junit.Test

class CardRequestTest {
    private val alice = Identity(UUID.randomUUID(), "Alice")
    private val full = alice.copy(card = CardContent(blocks = listOf(CardBlock(BlockType.Text, text = "hello"))), cardComplete = true)

    @Test fun `small request is assembled at MTU 23 without image allocation`() {
        val bytes = encodeIdentity(alice)
        val request = CardRequest()
        var offset = 0
        while (offset < bytes.size) {
            val frame = CardTransfer.frame(bytes, offset, 20)
            assertTrue(request.append(frame))
            offset += frame.size - CardTransfer.HEADER
        }
        assertTrue(request.complete)
        assertEquals(alice, request.peer)
        assertFalse(request.append(CardTransfer.frame(bytes, 0, 20)))
        assertFalse(CardRequest().append(ByteBuffer.allocate(9).putInt(0).putInt(50).put(0).array()))
    }

    @Test fun `full card requires accepted matching identity and cannot reopen the prompt`() {
        val sessions = GattExchangeSessions<String>()
        assertFalse(sessions.completeCard("a", full))
        sessions.onRequest("a", alice)
        assertFalse(sessions.completeCard("a", full))
        sessions.decide("a", true)
        assertFalse(sessions.completeCard("a", full.copy(id = UUID.randomUUID())))
        assertFalse(sessions.completeCard("a", alice))
        assertTrue(sessions.completeCard("a", full))
        assertEquals(ExchangeStatus.Accepted, sessions.status("a"))
        assertEquals(full, sessions.peerOf("a"))
        assertFalse(sessions.completeCard("a", full))
        sessions.onDisconnected("a")
        assertFalse(sessions.completeCard("a", full))
    }

    @Test fun `declined and expired requests never accept a full card`() {
        for (expire in listOf(false, true)) {
            val sessions = GattExchangeSessions<String>()
            sessions.onRequest("a", alice)
            if (expire) sessions.expire("a") else sessions.decide("a", false)
            assertFalse(sessions.completeCard("a", full))
            assertFalse(sessions.canRead("a"))
        }
    }
}
