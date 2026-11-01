package app.pengwa.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.test.*

class NearbyAvatarTest {
    private val key = UUID(0, 1)
    // Header fixture only; actual WebP encoding/decoding is covered on Android.
    private fun header(size: Int = 40, edge: Int = 48) = ByteArray(size).also {
        "RIFF".toByteArray().copyInto(it)
        "WEBPVP8 ".toByteArray().copyInto(it, 8)
        byteArrayOf(0x9d.toByte(), 1, 0x2a).copyInto(it, 23)
        ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(4, size - 8); putInt(16, size - 20)
            putShort(26, edge.toShort()); putShort(28, edge.toShort())
        }
    }

    @Test fun `头像和无头像往返 不改动名字预览协议`() {
        for (image in listOf(byteArrayOf(), header())) {
            val packet = NearbyAvatar.encode(key, image)
            assertTrue(packet.size <= 512)
            assertContentEquals(image, NearbyAvatar.decode(packet, key))
        }
        assertEquals(17, NearbyAvatar.encode(key, byteArrayOf()).size)
    }

    @Test fun `拒绝身份不匹配 过大 版本错误和截断的数据`() {
        val packet = NearbyAvatar.encode(key, header())
        assertNull(NearbyAvatar.decode(packet, UUID(0, 2)))
        assertNull(NearbyAvatar.decode(null, key))
        assertNull(NearbyAvatar.decode(packet.copyOf(16), key))
        assertNull(NearbyAvatar.decode(packet.copyOf(513), key))
        assertNull(NearbyAvatar.decode(packet.copyOf().also { it[0] = 2 }, key))
        assertNull(NearbyAvatar.decode(packet.copyOf(packet.size - 1), key))
    }

    @Test fun `解码前拒绝超过48像素 额外分块和超预算图片`() {
        assertFalse(NearbyAvatar.validImage(header(edge = 49)))
        assertFalse(NearbyAvatar.validImage(header(edge = 0)))
        assertFalse(NearbyAvatar.validImage(header(size = 496)))
        assertFalse(NearbyAvatar.validImage(header().also { it[12] = 'X'.code.toByte() }))
        assertFalse(NearbyAvatar.validImage(header().also { it[16] = 0 }))
        assertFailsWith<IllegalArgumentException> { NearbyAvatar.encode(key, header(edge = 1024)) }
    }

    @Test fun `头像晚于名字到达 地址轮换保留头像 等待状态不受影响`() {
        val list = NearbyList()
        list.onSeen("A", -50, 0)
        list.onPreview("A", "测试小熊", key)
        assertEquals("", list.snapshotForDisplay().single().avatar)
        list.onAvatar("A", UUID(0, 2), "wrong")
        assertEquals("", list.snapshotForDisplay().single().avatar)
        list.onAvatar("A", key, "thumbnail")
        list.beginExchange("A")
        list.onWaiting("A")
        list.onSeen("B", -40, 1000)
        list.onPreview("B", "测试小熊", key)
        assertEquals("thumbnail", list.snapshotForDisplay().single().avatar)
        assertEquals(PeerState.Waiting, list.snapshotForDisplay().single().state)
        list.onAvatar("B", key, "updated")
        assertEquals("updated", list.snapshotForDisplay().single().avatar)
        list.clear()
        list.onAvatar("B", key, "stale")
        assertTrue(list.snapshotForDisplay().isEmpty())
    }
}
