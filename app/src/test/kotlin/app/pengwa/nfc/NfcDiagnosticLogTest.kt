package app.pengwa.nfc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NfcDiagnosticLogTest {
    @Test fun `disabled log retains and outputs nothing`() {
        val output = mutableListOf<String>()
        val log = NfcDiagnosticLog(false, "device", output = output::add)
        log.record("event")
        assertTrue(log.lines.value.isEmpty())
        assertTrue(output.isEmpty())
        assertEquals("", log.report())
    }

    @Test fun `report keeps newest bounded history in chronological order and clear removes events`() {
        val log = NfcDiagnosticLog(true, "test device", clock = { 0 })
        repeat(NfcDiagnosticLog.MAX_LINES + 2) { log.record("event $it") }
        assertEquals(NfcDiagnosticLog.MAX_LINES, log.lines.value.size)
        assertTrue(log.lines.value.first().endsWith("event 2"))
        assertTrue(log.lines.value.last().endsWith("event 401"))
        assertTrue(log.report().startsWith("test device\nCaptured: 1970-01-01T00:00:00Z"))
        log.clear()
        assertTrue(log.lines.value.isEmpty())
        assertFalse(log.report().contains("event 401"))
    }

    @Test fun `concurrent reader and HCE events are not lost`() {
        val log = NfcDiagnosticLog(true, "device", clock = { 0 })
        val threads = List(8) { worker -> Thread { repeat(50) { log.record("worker $worker event $it") } } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(400, log.lines.value.toSet().size)
    }

    @Test fun `APDU summaries expose headers and status without identity payloads`() {
        val secret = "PRIVATE-NAME-AND-GUID".toByteArray()
        val summary = NfcDiagnosticLog.commandSummary(ApduProtocol.exchangeCommand(secret))
        assertTrue(summary.contains("EXCHANGE header=80E00000"))
        assertFalse(summary.contains(secret.toString(Charsets.UTF_8)))
        assertEquals("SW=9000 bytes=23", NfcDiagnosticLog.responseSummary(secret + ApduProtocol.SW_OK))
        assertEquals("SW=missing bytes=1", NfcDiagnosticLog.responseSummary(byteArrayOf(0x01)))
        assertTrue(NfcDiagnosticLog.commandSummary(ApduProtocol.selectCommand()).startsWith("SELECT private AID"))
    }
}
