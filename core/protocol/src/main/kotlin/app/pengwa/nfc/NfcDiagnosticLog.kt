package app.pengwa.nfc

import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local, bounded debug history. Callers supply metadata, never identities, UIDs or APDU payloads. */
class NfcDiagnosticLog(
    private val enabled: Boolean,
    private val header: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val output: (String) -> Unit = {},
) {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines = _lines.asStateFlow()

    @Synchronized
    fun record(message: String) {
        if (!enabled) return
        val line = "${Instant.ofEpochMilli(clock())} $message"
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
        output(line)
    }

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
    }

    fun report(): String = if (enabled) {
        "$header\nCaptured: ${Instant.ofEpochMilli(clock())}\n\n" +
            _lines.value.joinToString("\n").ifEmpty { "No NFC events recorded." }
    } else ""

    companion object {
        const val MAX_LINES = 400

        /** Only command class/header and lengths; no command data, tag UID or response body. */
        fun commandSummary(command: ByteArray): String {
            val kind = when {
                ApduProtocol.isExchange(command) -> "EXCHANGE"
                ApduProtocol.isSelect(command) -> when {
                    ApduProtocol.commandData(command)?.contentEquals(ApduProtocol.AID) == true -> "SELECT private AID"
                    ApduProtocol.commandData(command)?.contentEquals(T4tEmulator.AID) == true -> "SELECT NDEF AID"
                    else -> "SELECT other AID"
                }
                else -> "APDU"
            }
            return "$kind header=${command.take(4).hex()} bytes=${command.size}"
        }

        fun responseSummary(response: ByteArray): String =
            "SW=${if (response.size >= 2) response.takeLast(2).hex() else "missing"} bytes=${response.size}"

        private fun List<Byte>.hex(): String = joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }
}
