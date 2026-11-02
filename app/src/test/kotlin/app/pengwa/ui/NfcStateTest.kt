package app.pengwa.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NfcStateTest {
    @Test
    fun `NFC enabled is ready independently of the automatic transport role`() {
        assertEquals(NfcState.Ready, nfcStateFor(available = true, enabled = true))
    }

    @Test
    fun `disabled NFC offers settings while absent hardware offers alternatives`() {
        assertEquals(NfcState.Disabled, nfcStateFor(available = true, enabled = false))
        assertEquals(NfcState.Unsupported, nfcStateFor(available = false, enabled = false))
        assertEquals(NfcState.Unsupported, nfcStateFor(available = false, enabled = true))
    }
}
