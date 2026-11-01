package app.pengwa.ui

import app.pengwa.exchange.ExchangeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class NfcStateTest {
    @Test
    fun `只有读取硬件的设备仍能读取`() {
        assertEquals(NfcState.DisplayUnsupported, state(ExchangeMode.NfcDisplay, hce = false))
        assertEquals(NfcState.Ready, state(ExchangeMode.NfcRead, hce = false))
    }

    @Test
    fun `NFC 关闭或没有硬件时不启动会话`() {
        for (role in listOf(ExchangeMode.NfcDisplay, ExchangeMode.NfcRead)) {
            assertEquals(NfcState.Disabled, state(role, enabled = false))
            assertEquals(NfcState.Unsupported, state(role, available = false, enabled = false))
        }
    }

    private fun state(
        role: ExchangeMode, available: Boolean = true, enabled: Boolean = true,
        hce: Boolean = true,
    ) = nfcStateFor(role, available, enabled, hce)
}
