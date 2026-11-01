package app.pengwa.ble

/** Bounded notifications: one outstanding Android send, at most eight frames per application credit. */
class CardSendWindow(private val bytes: ByteArray) {
    init { require(bytes.size in 1..app.pengwa.identity.CardCodec.MAX_BYTES) }
    var offeredBytes = 0
        private set
    var failed = false
        private set
    private var pending = false
    private var frames = 0
    private var creditedBytes = 0

    fun nextFrame(frameSize: Int): ByteArray? {
        if (failed || pending || frames >= WINDOW_FRAMES || offeredBytes == bytes.size) return null
        val frame = CardTransfer.frame(bytes, offeredBytes, frameSize)
        offeredBytes += frame.size - CardTransfer.HEADER
        pending = true
        frames++
        return frame
    }

    fun onSent(success: Boolean) {
        if (!pending) return
        pending = false
        if (!success) failed = true
    }

    /** Credit may arrive before Android's send callback, but cannot release that outstanding send. */
    fun grant(offset: Int): Boolean {
        if (failed || frames != WINDOW_FRAMES || offset != offeredBytes || offset <= creditedBytes || offset >= bytes.size) return false
        creditedBytes = offset
        frames = 0
        return true
    }

    companion object {
        const val WINDOW_FRAMES = 8
        const val CREDIT_TOTAL = -2
    }
}
