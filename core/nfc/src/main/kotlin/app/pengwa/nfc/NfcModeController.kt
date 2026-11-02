package app.pengwa.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.util.Log
import app.pengwa.exchange.ExchangeMode

/** 前台 NFC 的唯一配置入口。切换角色先释放旧配置，避免 Reader Mode 与 HCE 前台优先级同时生效。 */
class NfcModeController(
    private val activity: Activity,
    private val service: ComponentName,
    private val diagnostics: NfcDiagnosticLog,
    private val inspectRouting: Boolean,
    private val onTag: NfcAdapter.ReaderCallback,
) {
    private val adapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(activity) }
    private var readerRequested = false
    private var preferredRequested = false
    private var current = ExchangeMode.None
    private var configurationGeneration = 0L

    /** 由主线程调用；进入角色时 Activity 必须已 resumed，暂停时在 super.onPause 之前释放。 */
    fun apply(mode: ExchangeMode) {
        val next = when (mode) {
            ExchangeMode.NfcDisplay, ExchangeMode.NfcRead -> mode
            else -> ExchangeMode.None
        }
        if (next == current && !readerRequested && !preferredRequested) return
        if (next == current && next != ExchangeMode.None) return
        diagnostics.record("Apply role=$next previous=$current reader=$readerRequested preferred=$preferredRequested")
        release()
        if (readerRequested || preferredRequested) {
            diagnostics.record("Role blocked: previous configuration not released reader=$readerRequested preferred=$preferredRequested")
            return
        }
        val nfc = adapter ?: run { diagnostics.record("NFC adapter unavailable"); return }
        if (next == ExchangeMode.None) return
        if (!nfc.isEnabled) { diagnostics.record("NFC disabled"); return }
        diagnostics.record("NFC enabled HCE=${activity.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)}")
        if (next == ExchangeMode.NfcDisplay &&
            !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        ) {
            Log.w(TAG, "HCE unavailable; use reader role or QR")
            diagnostics.record("HCE unavailable; display role not started")
            return
        }
        current = next
        Log.d(TAG, "NFC role=$next sdk=${Build.VERSION.SDK_INT}")
        when (next) {
            ExchangeMode.NfcRead -> {
                diagnostics.record("Reader flags=NFC_A|SKIP_NDEF_CHECK platformSounds=enabled")
                readerRequested = true
                val generation = configurationGeneration
                attempt("enableReaderMode") {
                    nfc.enableReaderMode(
                        activity, { tag ->
                            activity.runOnUiThread {
                                if (generation == configurationGeneration && current == ExchangeMode.NfcRead) {
                                    onTag.onTagDiscovered(tag)
                                } else {
                                    diagnostics.record("Reader callback ignored: stale configuration")
                                }
                            }
                        },
                        NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                        null,
                    )
                }
            }
            ExchangeMode.NfcDisplay -> {
                attempt("setPreferredService") {
                    val emulation = CardEmulation.getInstance(nfc)
                    if (inspectRouting) {
                        runCatching {
                            diagnostics.record("HCE walletDefault=${NfcWallet(activity, service).isDefault()} privateAidDefault=${emulation.isDefaultServiceForAid(service, "F050454E475741")} foregroundAllowed=${emulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_PAYMENT)}")
                            if (Build.VERSION.SDK_INT >= 35) {
                                diagnostics.record("Observe mode supported=${nfc.isObserveModeSupported} enabled=${nfc.isObserveModeEnabled}")
                            }
                        }.onFailure { diagnostics.record("HCE inspection failed=${it.javaClass.simpleName}") }
                    }
                    preferredRequested = true
                    val accepted = emulation.setPreferredService(
                        activity, service,
                    )
                    preferredRequested = accepted
                    diagnostics.record("setPreferredService accepted=$accepted")
                    if (accepted) Log.d(TAG, "setPreferredService=true")
                    else Log.w(TAG, "setPreferredService=false; system routing may take priority")
                }
            }
            else -> Unit
        }
    }

    private fun release() {
        val nfc = adapter ?: return
        configurationGeneration++
        current = ExchangeMode.None
        if (readerRequested) attempt("disableReaderMode") {
            nfc.disableReaderMode(activity)
            readerRequested = false
        }
        if (preferredRequested) attempt("unsetPreferredService") {
            val removed = CardEmulation.getInstance(nfc).unsetPreferredService(activity)
            Log.d(TAG, "unsetPreferredService=$removed")
            diagnostics.record("unsetPreferredService accepted=$removed")
            preferredRequested = !removed
        }
    }

    private inline fun attempt(operation: String, block: () -> Unit) {
        runCatching(block)
            .onSuccess {
                Log.d(TAG, operation)
                diagnostics.record("$operation returned normally")
            }
            .onFailure {
                Log.w(TAG, "$operation failed", it)
                diagnostics.record("$operation failed=${it.javaClass.simpleName}")
            }
    }

    private companion object {
        const val TAG = "PengWaNfc"
    }
}
