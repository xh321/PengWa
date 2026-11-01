package app.pengwa.exchange

import kotlin.test.*
import org.junit.Test

class ExchangeTimingTest {
    @Test fun `stages use monotonic elapsed and delta times without card contents`() {
        var now = 100L
        val lines = mutableListOf<String>()
        val timing = ExchangeTiming("NFC", { now }, lines::add)
        timing.mark("identified")
        now = 160
        timing.mark("uploaded", bytes = 32768, mtu = 517)
        now = 190
        timing.mark("stored")
        assertTrue(lines[0].contains("elapsedMs=0 stageMs=0"))
        assertTrue(lines[1].contains("elapsedMs=60 stageMs=60 bytes=32768 mtu=517"))
        assertTrue(lines[2].contains("elapsedMs=90 stageMs=30"))
    }
}
