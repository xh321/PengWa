package app.pengwa.ble

import app.pengwa.identity.toBytes
import app.pengwa.identity.uuidFromBytes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Public preview only; never contains the complete card or full-size avatar. */
object NearbyAvatar {
    const val MAX_PACKET_BYTES = 512
    const val HEADER_BYTES = 17
    const val MAX_IMAGE_BYTES = MAX_PACKET_BYTES - HEADER_BYTES
    const val MAX_EDGE = 48
    private const val VERSION: Byte = 1

    fun encode(key: UUID, image: ByteArray): ByteArray {
        require(validImage(image))
        return byteArrayOf(VERSION) + key.toBytes() + image
    }

    /** Empty bytes explicitly mean no public avatar; null means malformed or wrong identity. */
    fun decode(packet: ByteArray?, expectedKey: UUID): ByteArray? {
        if (packet == null || packet.size !in HEADER_BYTES..MAX_PACKET_BYTES || packet[0] != VERSION) return null
        if (uuidFromBytes(packet.copyOfRange(1, HEADER_BYTES)) != expectedKey) return null
        return packet.copyOfRange(HEADER_BYTES, packet.size).takeIf(::validImage)
    }

    /** Accept only bounded lossy WebP frames; check dimensions before any bitmap allocation. */
    fun validImage(image: ByteArray): Boolean {
        if (image.isEmpty()) return true
        if (image.size !in 30..MAX_IMAGE_BYTES) return false
        if (!image.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) ||
            !image.copyOfRange(8, 16).contentEquals("WEBPVP8 ".toByteArray(Charsets.US_ASCII)) ||
            !image.copyOfRange(23, 26).contentEquals(byteArrayOf(0x9d.toByte(), 1, 0x2a))) return false
        val data = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN)
        val chunkSize = data.getInt(16).toLong()
        return data.getInt(4).toLong() + 8 == image.size.toLong() &&
            chunkSize >= 10 && chunkSize + 20 + (chunkSize and 1) == image.size.toLong() &&
            (data.getShort(26).toInt() and 0x3fff) in 1..MAX_EDGE &&
            (data.getShort(28).toInt() and 0x3fff) in 1..MAX_EDGE
    }
}
