package app.pengwa.identity

import app.pengwa.ble.decodeIdentity
import app.pengwa.ble.encodeIdentity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.URI
import java.util.Base64

/** Images are bounded JPEG thumbnails, embedded so received cards work offline. */
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
        return bytes.size in 4..MAX_IMAGE_BYTES && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
            bytes[bytes.size - 2] == 0xff.toByte() && bytes.last() == 0xd9.toByte()
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

    fun encode(identity: Identity): ByteArray {
        require(validate(identity.card) == null) { validate(identity.card).orEmpty() }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(0x50574332) // PWC2
            val basic = encodeIdentity(identity)
            data.writeInt(basic.size)
            data.write(basic)
            data.writeUTF(identity.card.avatar)
            data.writeInt(identity.card.blocks.size)
            identity.card.blocks.forEach {
                data.writeUTF(it.type.name)
                data.writeUTF(it.title)
                data.writeUTF(it.text)
                data.writeUTF(it.image)
                data.writeUTF(it.url)
            }
        }
        return output.toByteArray().also { require(it.size <= MAX_BYTES) { "卡片超过 96 KB，请减少图片" } }
    }

    fun decode(bytes: ByteArray): Identity? = runCatching {
        require(bytes.size in 12..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readInt() == 0x50574332)
            val size = data.readInt()
            require(size in 18..49)
            val basic = decodeIdentity(ByteArray(size).also(data::readFully)) ?: error("identity")
            val avatar = data.readUTF()
            val count = data.readInt()
            require(count in 0..MAX_BLOCKS)
            val blocks = List(count) { CardBlock(BlockType.valueOf(data.readUTF()), data.readUTF(), data.readUTF(), data.readUTF(), data.readUTF()) }
            val card = CardContent(avatar, blocks)
            require(data.available() == 0 && validate(card) == null)
            basic.copy(card = card, cardComplete = true)
        }
    }.getOrNull()
}
