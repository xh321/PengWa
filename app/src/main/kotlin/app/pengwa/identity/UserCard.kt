package app.pengwa.identity

import app.pengwa.ble.decodeIdentity
import app.pengwa.ble.encodeIdentity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.URI
import java.util.Base64

/** Images are bounded JPEG/PNG thumbnails, embedded so received cards work offline. */
enum class BlockType { Text, Link, Social, Image }
data class CardBlock(val type: BlockType, val title: String = "", val text: String = "", val image: String = "", val url: String = "")
data class CardContent(val avatar: String = "", val blocks: List<CardBlock> = emptyList())

object CardCodec {
    const val MAX_BYTES = 96 * 1024
    const val MAX_BLOCKS = 12
    const val MAX_IMAGE_BYTES = 12 * 1024
    const val MAX_TEXT = 1000

    fun validLink(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme?.lowercase() in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    private fun validImage(value: String): Boolean {
        if (value.isEmpty()) return true
        if (value.length > MAX_IMAGE_BYTES * 4 / 3 + 4) return false
        val bytes = runCatching { Base64.getDecoder().decode(value) }.getOrNull() ?: return false
        if (bytes.size !in 4..MAX_IMAGE_BYTES) return false
        val jpeg = bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
            bytes[bytes.size - 2] == 0xff.toByte() && bytes.last() == 0xd9.toByte()
        val png = bytes.size >= 45 &&
            bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)) &&
            bytes.takeLast(12).toByteArray().contentEquals(byteArrayOf(0, 0, 0, 0, 0x49, 0x45, 0x4e, 0x44, 0xae.toByte(), 0x42, 0x60, 0x82.toByte()))
        return jpeg || png
    }

    fun validate(card: CardContent): String? = when {
        card.blocks.size > MAX_BLOCKS -> "最多添加 $MAX_BLOCKS 个内容块"
        !validImage(card.avatar) -> "头像格式或大小不符合要求"
        card.blocks.any { it.title.length > 60 || it.text.length > MAX_TEXT || it.url.length > MAX_TEXT } -> "标题最多 60 字，正文最多 $MAX_TEXT 字"
        card.blocks.any { !validImage(it.image) } -> "图片格式或大小不符合要求"
        card.blocks.any { it.type == BlockType.Link && !validLink(it.text) } -> "链接需要完整的 http 或 https 地址"
        card.blocks.any { it.url.isNotEmpty() && !validLink(it.url) } -> "社媒主页需要完整的 http 或 https 地址"
        card.blocks.any { it.type == BlockType.Image && it.image.isEmpty() } -> "请为图片块选择图片"
        else -> null
    }

    fun encode(identity: Identity): ByteArray = encode(identity, binaryImages = false)

    fun encodeBinary(identity: Identity): ByteArray = encode(identity, binaryImages = true)

    private fun encode(identity: Identity, binaryImages: Boolean): ByteArray {
        require(validate(identity.card) == null) { validate(identity.card).orEmpty() }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(if (binaryImages) 0x50574333 else 0x50574332) // PWC3 / PWC2
            val basic = encodeIdentity(identity)
            data.writeInt(basic.size)
            data.write(basic)
            writeImage(data, identity.card.avatar, binaryImages)
            data.writeInt(identity.card.blocks.size)
            identity.card.blocks.forEach {
                data.writeUTF(it.type.name)
                data.writeUTF(it.title)
                data.writeUTF(it.text)
                writeImage(data, it.image, binaryImages)
                data.writeUTF(it.url)
            }
        }
        return output.toByteArray().also { require(it.size <= MAX_BYTES) { "卡片超过 96 KB，请减少图片" } }
    }

    private fun writeImage(data: DataOutputStream, value: String, binary: Boolean) {
        if (!binary) { data.writeUTF(value); return }
        val bytes = Base64.getDecoder().decode(value)
        data.writeInt(bytes.size)
        data.write(bytes)
    }

    private fun readImage(data: DataInputStream, binary: Boolean): String {
        if (!binary) return data.readUTF()
        val length = data.readInt()
        require(length in 0..MAX_IMAGE_BYTES && length <= data.available())
        return Base64.getEncoder().encodeToString(ByteArray(length).also(data::readFully))
    }

    /** Network protocol accepts only PWC3. PWC2 is retained solely for local storage. */
    fun decodeBinary(bytes: ByteArray): Identity? = bytes.takeIf {
        it.size >= 4 && java.nio.ByteBuffer.wrap(it).int == 0x50574333
    }?.let(::decode)

    fun decode(bytes: ByteArray): Identity? = runCatching {
        require(bytes.size in 12..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            val magic = data.readInt()
            require(magic == 0x50574332 || magic == 0x50574333)
            val binaryImages = magic == 0x50574333
            val size = data.readInt()
            require(size in 18..49)
            val basic = decodeIdentity(ByteArray(size).also(data::readFully)) ?: error("identity")
            val avatar = readImage(data, binaryImages)
            val count = data.readInt()
            require(count in 0..MAX_BLOCKS)
            val blocks = List(count) { CardBlock(BlockType.valueOf(data.readUTF()), data.readUTF(), data.readUTF(), readImage(data, binaryImages), data.readUTF()) }
            val card = CardContent(avatar, blocks)
            require(data.available() == 0 && validate(card) == null)
            basic.copy(card = card, cardComplete = true).also { if (binaryImages) encode(it) }
        }
    }.getOrNull()
}
