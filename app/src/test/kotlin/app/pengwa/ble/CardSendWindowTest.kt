package app.pengwa.ble

import app.pengwa.identity.*
import java.util.UUID
import kotlin.test.*
import org.junit.Test

class CardSendWindowTest {
    private val data = CardCodec.encodeBinary(Identity(UUID.randomUUID(), "Alice", CardContent(blocks =
        List(12) { CardBlock(BlockType.Text, text = "hello".repeat(180)) })))

    @Test fun `window streams exact bytes at every supported MTU including credit before send callback`() {
        for (size in listOf(20, 61, 244, 512)) {
            val sender = CardSendWindow(data)
            val receiver = CardAssembler()
            var frames = 0
            while (!receiver.complete) {
                val frame = assertNotNull(sender.nextFrame(size))
                assertTrue(receiver.append(frame))
                assertNull(sender.nextFrame(size), "must await Android send callback")
                frames++
                if (frames % CardSendWindow.WINDOW_FRAMES == 0 && !receiver.complete) {
                    assertTrue(sender.grant(receiver.received), "credit may beat onNotificationSent")
                    assertNull(sender.nextFrame(size), "credit does not release outstanding Android send")
                }
                sender.onSent(true)
            }
            assertContentEquals(data, receiver.result())
            assertEquals(data.size, sender.offeredBytes)
            assertNull(sender.nextFrame(size))
            assertFalse(sender.grant(data.size))
        }
    }

    @Test fun `window stops without credit and rejects gaps duplicate credit and wrong offsets`() {
        val sender = CardSendWindow(data)
        repeat(CardSendWindow.WINDOW_FRAMES) {
            assertNotNull(sender.nextFrame(20))
            sender.onSent(true)
        }
        assertNull(sender.nextFrame(20))
        assertFalse(sender.grant(sender.offeredBytes + 1))
        assertFalse(sender.grant(sender.offeredBytes - 1))
        assertTrue(sender.grant(sender.offeredBytes))
        assertFalse(sender.grant(sender.offeredBytes))
        assertNotNull(sender.nextFrame(20))
    }

    @Test fun `last partial and exact window boundaries finish without extra credit`() {
        for (length in listOf(1, 12, 13, 504, 4032, 4033, CardCodec.MAX_BYTES)) {
            val bytes = ByteArray(length) { (it % 251).toByte() }
            val sender = CardSendWindow(bytes)
            val receiver = CardAssembler()
            var frames = 0
            while (!receiver.complete) {
                assertTrue(receiver.append(assertNotNull(sender.nextFrame(512))))
                sender.onSent(true)
                frames++
                if (frames % 8 == 0 && !receiver.complete) assertTrue(sender.grant(receiver.received))
            }
            assertContentEquals(bytes, receiver.result())
            assertNull(sender.nextFrame(512))
        }
    }

    @Test fun `failed Android send permanently stops transfer`() {
        val sender = CardSendWindow(data)
        assertNotNull(sender.nextFrame(20))
        sender.onSent(false)
        assertTrue(sender.failed)
        assertNull(sender.nextFrame(20))
        assertFalse(sender.grant(sender.offeredBytes))
    }
}
