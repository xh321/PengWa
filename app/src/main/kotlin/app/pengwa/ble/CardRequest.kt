package app.pengwa.ble

import app.pengwa.identity.Identity
import java.nio.ByteBuffer

/** Tiny request works at MTU 23; never allocates a card/image before consent. */
class CardRequest {
    private val assembler = CardAssembler()
    val complete: Boolean get() = assembler.complete
    val peer: Identity? get() = assembler.result()?.let(::decodeIdentity)

    fun append(frame: ByteArray): Boolean {
        if (frame.size <= CardTransfer.HEADER) return false
        if (ByteBuffer.wrap(frame).getInt(4) !in 18..49) return false
        return assembler.append(frame)
    }
}
