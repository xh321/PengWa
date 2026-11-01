package app.pengwa.ui

/** Five consecutive taps, resetting after a pause or a successful trigger. */
internal class DebugTapCounter {
    private var count = 0
    private var lastTap: Long? = null

    fun tap(nowMillis: Long): Boolean {
        val previous = lastTap
        if (previous == null || nowMillis - previous !in 0..1500) count = 0
        lastTap = nowMillis
        count++
        if (count < 5) return false
        count = 0
        lastTap = null
        return true
    }
}
