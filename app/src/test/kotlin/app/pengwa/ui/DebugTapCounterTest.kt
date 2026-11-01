package app.pengwa.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugTapCounterTest {
    @Test fun `only the fifth consecutive tap opens debug`() {
        val taps = DebugTapCounter()
        repeat(4) { assertFalse(taps.tap(it * 200L)) }
        assertTrue(taps.tap(800))
        assertFalse(taps.tap(1000))
    }

    @Test fun `pause resets partial gesture`() {
        val taps = DebugTapCounter()
        repeat(4) { assertFalse(taps.tap(it * 100L)) }
        repeat(4) { assertFalse(taps.tap(2000 + it * 100L)) }
        assertTrue(taps.tap(2400))
    }

    @Test fun `clock going backwards starts a new gesture`() {
        val taps = DebugTapCounter()
        repeat(4) { assertFalse(taps.tap(1000 + it * 100L)) }
        assertFalse(taps.tap(0))
        repeat(3) { assertFalse(taps.tap(100 + it * 100L)) }
        assertTrue(taps.tap(400))
    }
}
