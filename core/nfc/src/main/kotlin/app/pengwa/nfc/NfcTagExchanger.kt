package app.pengwa.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import android.os.SystemClock
import app.pengwa.identity.Identity
import app.pengwa.exchange.ExchangeMode
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * 读取一方：拿到 Reader Mode 回调的 Tag，在 IsoDep 上跑 [runInitiator]。
 * 会阻塞，必须在后台线程调用；[onPeer] 和 [onFailure] 在主线程回调。
 */
class NfcTagExchanger(
    private val identityProvider: () -> Identity?,
    private val modeProvider: () -> ExchangeMode,
    private val diagnostics: NfcDiagnosticLog,
    private val onPeer: (Identity) -> Unit,
    private val onFailure: () -> Unit,
) {
    private val exchanges = AtomicInteger()

    fun exchange(tag: Tag): ExchangeResult {
        val attempt = exchanges.incrementAndGet()
        val started = SystemClock.elapsedRealtime()
        diagnostics.record("Reader #$attempt start role=${modeProvider()}")
        val isoDep = IsoDep.get(tag) ?: run {
            diagnostics.record("Reader #$attempt incompatible: no IsoDep")
            return ExchangeResult.NotCompatible
        }
        val me = identityProvider()
            ?: run {
                diagnostics.record("Reader #$attempt failed: local identity missing")
                return ExchangeResult.Failed("还没有设置显示名")
            }

        val result = try {
            isoDep.connect()
            isoDep.timeout = TIMEOUT_MS
            diagnostics.record("Reader #$attempt connected timeoutMs=$TIMEOUT_MS maxTransceive=${isoDep.maxTransceiveLength}")
            runInitiator(me, discoveryOnly = true) { command ->
                val sent = SystemClock.elapsedRealtime()
                diagnostics.record("Reader #$attempt TX ${NfcDiagnosticLog.commandSummary(command)}")
                val stage = if (ApduProtocol.isSelect(command)) "SELECT private AID" else "EXCHANGE"
                Log.d(TAG, "reader $stage")
                isoDep.transceive(command).also { response ->
                    diagnostics.record("Reader #$attempt RX ${NfcDiagnosticLog.responseSummary(response)} elapsedMs=${SystemClock.elapsedRealtime() - sent}")
                    Log.d(TAG, "reader $stage success=${ApduProtocol.isSuccess(response)}")
                }
            }
        } catch (e: IOException) {
            diagnostics.record("Reader #$attempt failed=${e.javaClass.simpleName} elapsedMs=${SystemClock.elapsedRealtime() - started}")
            // 碰的时间太短、手机移开了
            ExchangeResult.Failed("连接中断：${e.message}")
        } finally {
            runCatching { isoDep.close() }
                .onFailure { diagnostics.record("Reader #$attempt close failed=${it.javaClass.simpleName}") }
        }

        when (result) {
            is ExchangeResult.Peer -> {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onPeer(result.identity) }
                diagnostics.record("Reader #$attempt identified; starting BLE elapsedMs=${SystemClock.elapsedRealtime() - started}")
            }
            is ExchangeResult.Failed -> {
                Log.w(TAG, "NFC 发现失败：${result.reason}")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onFailure()
                }
            }
            ExchangeResult.NotCompatible -> Log.d(TAG, "不是碰碰娃，忽略")
        }
        if (result !is ExchangeResult.Peer) diagnostics.record("Reader #$attempt result=${result.javaClass.simpleName}")
        return result
    }

    private companion object {
        const val TAG = "PengWaNfc"
        const val TIMEOUT_MS = 3000
    }
}
