package app.pengwa.identity

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Shared identity wire format, independent of the transport. */
const val IDENTITY_VERSION: Byte = 1
const val IDENTITY_HEADER_BYTES = 17
const val IDENTITY_MAX_BYTES = IDENTITY_HEADER_BYTES + DISPLAY_NAME_MAX_BYTES

/** Normalize outgoing names once for identity and BLE preview packets. */
fun encodeDisplayName(displayName: String): ByteArray {
    val name = normalizeDisplayName(displayName).toByteArray(Charsets.UTF_8)
    require(name.size <= DISPLAY_NAME_MAX_BYTES) { "显示名超过 $DISPLAY_NAME_MAX_BYTES 字节" }
    return name
}

/** 名片编码：`[版本 1 字节][GUID 16 字节][UTF-8 显示名]`，最长 17 + 32 = 49 字节。 */
fun encodeIdentity(identity: Identity): ByteArray {
    val name = encodeDisplayName(identity.displayName)
    return ByteBuffer.allocate(IDENTITY_HEADER_BYTES + name.size)
        .put(IDENTITY_VERSION)
        .put(identity.id.toBytes())
        .put(name)
        .array()
}

/**
 * 解码对方发来的名片。对方的数据不可信，任何不合法的情况都返回 null：
 * 版本不对、长度不够、名字超长、UTF-8 非法，或名字没通过本地同一套 [validateDisplayName] 规则。
 */
fun decodeIdentity(bytes: ByteArray?): Identity? {
    if (bytes == null || bytes.size !in (IDENTITY_HEADER_BYTES + 1)..IDENTITY_MAX_BYTES) return null
    if (bytes[0] != IDENTITY_VERSION) return null
    val nameBytes = bytes.size - IDENTITY_HEADER_BYTES

    val id = uuidFromBytes(bytes.copyOfRange(1, IDENTITY_HEADER_BYTES))
    val name = decodeDisplayName(bytes, IDENTITY_HEADER_BYTES, nameBytes) ?: return null
    return Identity(id, name)
}

/**
 * 严格按 UTF-8 解出名字，并要求它已经是规范形式、且通过显示名规则。
 * 要求"已规范化"是为了让同一个名字只有一种字节表示，对方没按协议编码就拒绝。
 */
fun decodeDisplayName(bytes: ByteArray, offset: Int, length: Int): String? {
    if (length !in 1..DISPLAY_NAME_MAX_BYTES || offset < 0 || offset > bytes.size - length) return null
    val name = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, length))
            .toString()
    } catch (_: CharacterCodingException) {
        return null
    }
    if (name != normalizeDisplayName(name)) return null
    if (validateDisplayName(name) != null) return null
    return name
}
