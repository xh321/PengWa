package app.pengwa.exchange

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutomaticNfcSessionTest {
    @Test
    fun `automatic switching waits until the NFC transaction ends`() {
        for (role in listOf(ExchangeMode.NfcRead, ExchangeMode.NfcDisplay)) {
            val state = ExchangeModeState()
            val owner = state.beginAutomaticNfc(role)
            val lease = assertNotNull(state.beginNfcExchange(role))
            val displaySession = state.nfcDisplaySession
            repeat(10) { assertFalse(state.advanceAutomaticNfc(owner)) }
            assertEquals(role, state.current)
            assertEquals(displaySession, state.nfcDisplaySession)
            assertNull(state.beginNfcExchange(role)) // duplicate tag callbacks cannot race the first
            state.endNfcExchange(lease)
            assertTrue(state.advanceAutomaticNfc(owner))
            assertNotEquals(role, state.current)
        }
    }

    @Test
    fun `leaving the page invalidates an in flight exchange immediately`() {
        val state = ExchangeModeState()
        val session = AutomaticNfcSession(state, canDisplay = false)
        val lease = assertNotNull(state.beginNfcExchange(ExchangeMode.NfcRead))
        state.set(ExchangeMode.None)
        assertFalse(state.isNfcExchangeActive(lease))
        assertFalse(session.advance())
        assertEquals(ExchangeMode.None, state.current)
        assertNull(state.nfcDisplaySession)
    }

    @Test
    fun `old cleanup cannot stop a newer foreground session`() {
        val state = ExchangeModeState()
        val old = AutomaticNfcSession(state, canDisplay = true, Random(1))
        val current = AutomaticNfcSession(state, canDisplay = false)
        val lease = assertNotNull(state.beginNfcExchange(ExchangeMode.NfcRead))
        old.close()
        assertFalse(old.advance())
        assertEquals(ExchangeMode.NfcRead, state.current)
        assertTrue(state.isNfcExchangeActive(lease))
        current.close()
        assertEquals(ExchangeMode.None, state.current)
        assertFalse(state.isNfcExchangeActive(lease))
    }

    @Test
    fun `old cleanup cannot stop the nearby Bluetooth page`() {
        val state = ExchangeModeState()
        val session = AutomaticNfcSession(state, canDisplay = true)
        state.set(ExchangeMode.Bluetooth)
        session.close()
        assertFalse(session.advance())
        assertEquals(ExchangeMode.Bluetooth, state.current)
    }

    @Test
    fun `an old transaction cannot release a new transaction lock`() {
        val state = ExchangeModeState()
        AutomaticNfcSession(state, canDisplay = false)
        val oldLease = assertNotNull(state.beginNfcExchange(ExchangeMode.NfcRead))
        val newOwner = state.beginAutomaticNfc(ExchangeMode.NfcRead)
        val newLease = assertNotNull(state.beginNfcExchange(ExchangeMode.NfcRead))
        state.endNfcExchange(oldLease)
        assertFalse(state.isNfcExchangeActive(oldLease))
        assertTrue(state.isNfcExchangeActive(newLease))
        assertFalse(state.advanceAutomaticNfc(newOwner))
        state.endNfcExchange(newLease)
        assertTrue(state.advanceAutomaticNfc(newOwner))
    }

    @Test
    fun `a device without HCE keeps reading instead of switching to an unsupported role`() {
        val state = ExchangeModeState()
        val session = AutomaticNfcSession(state, canDisplay = false)
        repeat(20) { assertFalse(session.advance()) }
        assertEquals(ExchangeMode.NfcRead, state.current)
        assertNull(state.beginNfcExchange(ExchangeMode.NfcDisplay))
        session.close()
        assertEquals(ExchangeMode.None, state.current)
    }

    @Test
    fun `each display window starts a fresh APDU session`() {
        val state = ExchangeModeState()
        val owner = state.beginAutomaticNfc(ExchangeMode.NfcDisplay)
        val first = assertNotNull(state.nfcDisplaySession)
        state.advanceAutomaticNfc(owner)
        assertNull(state.nfcDisplaySession)
        state.advanceAutomaticNfc(owner)
        assertNotEquals(first, state.nfcDisplaySession)
    }

    @Test
    fun `two simultaneously opened phones get complementary windows with independent jitter`() {
        // Deterministic simulation, not a timing-dependent hardware success assertion.
        repeat(500) { seed ->
            val a = ExchangeModeState()
            val b = ExchangeModeState()
            val sa = AutomaticNfcSession(a, canDisplay = true, Random(seed * 2))
            val sb = AutomaticNfcSession(b, canDisplay = true, Random(seed * 2 + 1))
            var nextA = sa.nextDelayMillis()
            var nextB = sb.nextDelayMillis()
            var now = 0L
            var complementary = false
            while (now < 30_000L) {
                val next = minOf(nextA, nextB)
                if (a.current != b.current && next - now >= 500L) {
                    complementary = true
                    break
                }
                now = next
                if (now == nextA) { sa.advance(); nextA += sa.nextDelayMillis() }
                if (now == nextB) { sb.advance(); nextB += sb.nextDelayMillis() }
            }
            assertTrue(complementary, "seed=$seed")
            sa.close()
            sb.close()
        }
    }
}
