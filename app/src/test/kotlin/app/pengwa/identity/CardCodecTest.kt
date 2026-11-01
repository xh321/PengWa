package app.pengwa.identity

import app.pengwa.exchange.PeerDiscovery
import app.pengwa.ble.*
import app.pengwa.nfc.*
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import kotlin.test.*
import org.junit.Test

class CardCodecTest {
    private val jpeg = Base64.getEncoder().encodeToString(ByteArray(CardCodec.MAX_IMAGE_BYTES).also {
        it[0] = 0xff.toByte(); it[1] = 0xd8.toByte(); it[it.lastIndex - 1] = 0xff.toByte(); it[it.lastIndex] = 0xd9.toByte()
    })
    private val card = Identity(UUID.randomUUID(), "小桃", CardContent(jpeg, listOf(
        CardBlock(BlockType.Text, "关于我", "一起拍照吧"),
        CardBlock(BlockType.Link, "作品", "https://example.com"),
        CardBlock(BlockType.Social, "社媒", "@test", url = "https://example.com/me"),
        CardBlock(BlockType.Image, "小图", image = jpeg),
    )), cardComplete = true)

    @Test fun `rich card round trips through smallest and largest MTUs`() {
        val data = CardCodec.encode(card)
        for (mtu in listOf(23, 64, 247, 517)) {
            val assembler = CardAssembler()
            var offset = 0
            while (offset < data.size) {
                val frame = CardTransfer.frame(data, offset, minOf(mtu - 3, 512))
                assertTrue(assembler.append(frame))
                offset += frame.size - CardTransfer.HEADER
            }
            assertEquals(card, CardCodec.decode(assembler.result()!!))
        }
    }
    @Test fun `truncation unknown version trailing bytes and invalid names are rejected`() {
        val data = CardCodec.encode(card)
        assertNull(CardCodec.decode(data.dropLast(1).toByteArray()))
        assertNull(CardCodec.decode(data + byteArrayOf(0)))
        assertNull(CardCodec.decode(data.copyOf().also { it[0] = 0 }))
        assertNull(CardCodec.decode(ByteArray(CardCodec.MAX_BYTES + 1)))
    }
    @Test fun `bounds and unsafe links are rejected before storage`() {
        assertNotNull(CardCodec.validate(CardContent(blocks = List(13) { CardBlock(BlockType.Text) })))
        assertNotNull(CardCodec.validate(CardContent(avatar = "not an image")))
        for (url in listOf("javascript:alert(1)", "file:///tmp/test", "https://user:password@example.com", "relative")) {
            assertFalse(CardCodec.validLink(url))
            assertFailsWith<IllegalArgumentException> { CardCodec.encode(card.copy(card = CardContent(blocks = listOf(CardBlock(BlockType.Link, text = url))))) }
        }
        assertNotNull(CardCodec.validate(CardContent(blocks = listOf(CardBlock(BlockType.Text, text = "x".repeat(1001))))))
    }
    @Test fun `many individually valid images cannot exceed the whole card limit`() {
        val tooLarge = card.copy(card = CardContent(jpeg, List(6) { CardBlock(BlockType.Image, image = jpeg) }))
        assertNull(CardCodec.validate(tooLarge.card))
        assertFailsWith<IllegalArgumentException> { CardCodec.encode(tooLarge) }
    }
    @Test fun `assembler rejects gaps changed totals duplicates and oversized allocations`() {
        val data = CardCodec.encode(card)
        val assembler = CardAssembler()
        assertFalse(assembler.append(CardTransfer.frame(data, 1, 20)))
        assertNull(assembler.result())
        val first = CardTransfer.frame(data, 0, 20)
        assertTrue(assembler.append(first))
        assertFalse(assembler.append(first))
        val changed = CardTransfer.frame(data, assembler.received, 20).also { ByteBuffer.wrap(it).putInt(4, data.size + 1) }
        assertFalse(assembler.append(changed))
        assertFalse(CardAssembler().append(ByteBuffer.allocate(9).putInt(0).putInt(Int.MAX_VALUE).put(0).array()))
        assertFalse(CardAssembler().append(CardTransfer.ACK))
    }
    @Test fun `transparent PNG survives card transfer and malformed PNG is rejected`() {
        val png = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAABCAYAAAD0In+KAAAAD0lEQVR4nGP4z8DQwAAEAAqBAYANgP7nAAAAAElFTkSuQmCC"
        val identity = card.copy(card = CardContent(blocks = listOf(CardBlock(BlockType.Image, image = png))))
        assertEquals(identity, CardCodec.decode(CardCodec.encode(identity)))
        val bytes = Base64.getDecoder().decode(png)
        for (invalid in listOf(bytes.dropLast(1).toByteArray(), bytes.copyOf().also { it[0] = 0 })) {
            assertNotNull(CardCodec.validate(CardContent(blocks = listOf(CardBlock(BlockType.Image,
                image = Base64.getEncoder().encodeToString(invalid))))))
        }
    }
    @Test fun `binary images reduce wire bytes and preserve legacy storage and contents`() {
        val legacy = CardCodec.encode(card)
        val binary = CardCodec.encodeBinary(card)
        assertEquals(card, CardCodec.decodeBinary(binary))
        assertNull(CardCodec.decodeBinary(legacy))
        assertEquals(card, CardCodec.decode(legacy))
        assertEquals(2 * (CardCodec.MAX_IMAGE_BYTES / 3) - 2 * (card.card.blocks.size + 1), legacy.size - binary.size)
        assertContentEquals(legacy, CardCodec.encode(CardCodec.decode(binary)!!))
        for (mtu in listOf(23, 64, 247, 517)) {
            val assembler = CardAssembler()
            while (!assembler.complete) assertTrue(assembler.append(CardTransfer.frame(binary, assembler.received, minOf(mtu - 3, 512))))
            assertEquals(card, CardCodec.decode(assembler.result()!!))
        }
    }

    @Test fun `binary image lengths are bounded before allocation`() {
        val binary = CardCodec.encodeBinary(card)
        val avatarOffset = 8 + encodeIdentity(card).size
        for (length in listOf(-1, Int.MAX_VALUE, CardCodec.MAX_IMAGE_BYTES + 1)) {
            assertNull(CardCodec.decode(binary.copyOf().also { ByteBuffer.wrap(it).putInt(avatarOffset, length) }))
        }
        assertNull(CardCodec.decode(binary.dropLast(1).toByteArray()))
        assertNull(CardCodec.decode(binary + byteArrayOf(0)))
        val empty = card.copy(card = CardContent())
        assertEquals(empty, CardCodec.decode(CardCodec.encodeBinary(empty)))
    }

    @Test fun `NFC discovery contains only identifier and never full card`() {
        val peer = card.copy(id = UUID.randomUUID(), displayName = "小蓝")
        var discovered: Identity? = null
        val handler = ApduHandler({ peer }, { discovered = it }, discoveryOnly = true)
        val result = runInitiator(card, true) { command ->
            if (ApduProtocol.isExchange(command)) assertEquals(17, ApduProtocol.commandData(command)!!.size)
            handler.process(command)
        } as ExchangeResult.Peer
        assertEquals(peer.id, result.identity.id)
        assertEquals(card.id, discovered!!.id)
        assertEquals(CardContent(), discovered!!.card)
        assertNull(decodeIdentity(PeerDiscovery.encode(card)))
        assertNull(PeerDiscovery.decode(encodeIdentity(card)))
        assertTrue(runInitiator(card, true, ApduHandler({ peer }, {})::process) is ExchangeResult.Failed)
    }
}
