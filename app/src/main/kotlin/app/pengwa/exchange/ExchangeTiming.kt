package app.pengwa.exchange

import java.util.concurrent.atomic.AtomicLong

/** Monotonic stage timings. Only local attempt IDs, stage names and counters leave the process. */
class ExchangeTiming(
    private val source: String,
    private val clock: () -> Long,
    private val output: (String) -> Unit,
) {
    private val attempt = nextAttempt.incrementAndGet()
    private val started = clock()
    private var previous = started

    @Synchronized
    fun mark(stage: String, bytes: Int? = null, mtu: Int? = null) {
        val now = clock()
        output("attempt=$attempt source=$source stage=$stage elapsedMs=${now - started} stageMs=${now - previous}" +
            (bytes?.let { " bytes=$it" } ?: "") + (mtu?.let { " mtu=$it" } ?: ""))
        previous = now
    }

    private companion object { val nextAttempt = AtomicLong() }
}
