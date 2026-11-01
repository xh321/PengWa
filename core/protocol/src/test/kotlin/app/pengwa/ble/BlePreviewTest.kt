package app.pengwa.ble

import app.pengwa.identity.Identity
import java.util.UUID
import kotlin.test.assertNotEquals
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BlePreviewTest {
    @Test
    fun `预览编码是版本号加 UTF-8 名字 不含 ID`() {
        val bytes = encodePreview("小熊")
        assertEquals(1, bytes[0].toInt())
        assertContentEquals("小熊".toByteArray(Charsets.UTF_8), bytes.copyOfRange(1, bytes.size))
        assertEquals(1 + 6, bytes.size)
    }

    @Test
    fun `编码再解码得到同一个名字`() {
        assertEquals("Yuki 小熊 🐻", decodePreview(encodePreview("Yuki 小熊 🐻")))
    }

    @Test
    fun `最长的名字 32 字节也能往返`() {
        assertEquals("a".repeat(32), decodePreview(encodePreview("a".repeat(32))))
    }

    @Test
    fun `编码超长名字直接报错`() {
        assertFailsWith<IllegalArgumentException> { encodePreview("a".repeat(33)) }
    }

    @Test
    fun `null 太短 版本不对都解不出来`() {
        assertNull(decodePreview(null))
        assertNull(decodePreview(byteArrayOf()))
        assertNull(decodePreview(byteArrayOf(1)))
        assertNull(decodePreview(byteArrayOf(2) + "abc".toByteArray()))
    }

    @Test
    fun `对方发来超长名字时拒绝`() {
        assertNull(decodePreview(byteArrayOf(1) + "a".repeat(33).toByteArray()))
    }

    @Test
    fun `非法 UTF-8 拒绝`() {
        assertNull(decodePreview(byteArrayOf(1, 0xC3.toByte(), 0x28)))
    }

    @Test
    fun `对方发来带控制字符 零宽字符 或未规范化的名字时拒绝`() {
        assertNull(decodePreview(byteArrayOf(1) + "a\nb".toByteArray()))
        assertNull(decodePreview(byteArrayOf(1) + "a​b".toByteArray()))
        assertNull(decodePreview(byteArrayOf(1) + " abc".toByteArray()))
        assertNull(decodePreview(byteArrayOf(1) + "é".toByteArray()))
    }

    @Test
    fun `当前五个服务特征的 UUID 互不相同`() {
        val all = setOf(BleProtocol.PEER_PREVIEW_CHAR_UUID, BleProtocol.STATUS_CHAR_UUID, BleProtocol.CARD_CHAR_UUID, BleProtocol.REQUEST_CHAR_UUID, BleProtocol.STREAM_CHAR_UUID)
        assertEquals(5, all.size)
    }

    @Test
    fun `身份预览稳定且不随名字变化 不直接返回完整用户 ID`() {
        val id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
        val first = decodePeerPreview(encodePeerPreview(Identity(id, "小熊")))!!
        val renamed = decodePeerPreview(encodePeerPreview(Identity(id, "改名")))!!
        assertEquals("小熊", first.name)
        assertEquals(first.identityKey, renamed.identityKey)
        assertNotEquals(id, first.identityKey)
        assertNotEquals(first.identityKey, identityKey(UUID(0, 2)))
        assertEquals(49, encodePeerPreview(Identity(id, "a".repeat(32))).size)
        assertEquals("a".repeat(32), decodePeerPreview(encodePeerPreview(Identity(id, "a".repeat(32))))?.name)
    }

    @Test
    fun `身份预览拒绝坏版本 截断 超长和不合法名字`() {
        val valid = encodePeerPreview(Identity(UUID(0, 1), "abc"))
        assertNull(decodePeerPreview(null))
        assertNull(decodePeerPreview(valid.copyOf(17)))
        assertNull(decodePeerPreview(valid.copyOf().also { it[0] = 2 }))
        assertNull(decodePeerPreview(valid.copyOfRange(0, 17) + "a".repeat(33).toByteArray()))
        assertNull(decodePeerPreview(valid.copyOfRange(0, 17) + byteArrayOf(0xC3.toByte(), 0x28)))
        assertNull(decodePeerPreview(valid.copyOfRange(0, 17) + "a\nb".toByteArray()))
        assertNull(decodePeerPreview(valid.copyOfRange(0, 17) + " abc".toByteArray()))
    }
}
