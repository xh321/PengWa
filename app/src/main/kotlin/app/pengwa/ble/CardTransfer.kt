package app.pengwa.ble

import app.pengwa.identity.CardCodec
import java.nio.ByteBuffer

/** Application chunks work even with MTU 23. A frame carries [offset,total,payload]. */
object CardTransfer {
    const val HEADER = 8
    val ACK: ByteArray get() = ByteBuffer.allocate(HEADER).putInt(-1).putInt(0).array()
    fun frame(data: ByteArray, offset: Int, size: Int): ByteArray {
        require(data.size in 1..CardCodec.MAX_BYTES && offset in data.indices && size > HEADER)
        val end = minOf(data.size, offset + size - HEADER)
        return ByteBuffer.allocate(HEADER + end - offset).putInt(offset).putInt(data.size)
            .put(data, offset, end - offset).array()
    }
}

/** Rejects gaps, repeated chunks, changing lengths and oversized allocations. */
class CardAssembler {
    private var buffer: ByteArray? = null
    var received: Int = 0
        private set
    val complete: Boolean get() = buffer != null && received == buffer!!.size
    fun append(frame: ByteArray): Boolean {
        if (frame.size <= CardTransfer.HEADER || complete) return false
        val input = ByteBuffer.wrap(frame)
        val offset = input.int
        val total = input.int
        if (total !in 1..CardCodec.MAX_BYTES || offset != received || input.remaining() > total - received) return false
        if (buffer == null) buffer = ByteArray(total)
        if (buffer!!.size != total) return false
        input.get(buffer!!, received, input.remaining())
        received += frame.size - CardTransfer.HEADER
        return true
    }
    fun result(): ByteArray? = buffer?.takeIf { complete }?.copyOf()
}
