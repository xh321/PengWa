package app.pengwa

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pengwa.ble.BleBroadcastService
import app.pengwa.exchange.ExchangeMode
import app.pengwa.nfc.NdefLaunchParser
import app.pengwa.nfc.NfcModeController
import app.pengwa.nfc.NfcTagExchanger
import app.pengwa.ui.PengWaRoot
import app.pengwa.ui.theme.PengWaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** 通知栏按钮带来的"请切到某一页"请求；界面处理完后清空。 */
    private var requestedTab by mutableStateOf<Int?>(null)

    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private val nfcExchanger by lazy {
        NfcTagExchanger(
            identityProvider = { pengWa.identityRepository.load() },
            modeProvider = { pengWa.exchangeMode.current },
            diagnostics = pengWa.nfcDiagnostics,
            onPeer = { pengWa.bleExchange.discover(it, initiate = true) },
            onFailure = {
                pengWa.bleExchange.discoveryFailed("NFC 识别失败，请确认双方使用新版应用后重新碰一碰")
            },
        )
    }

    private val nfcMode by lazy {
        NfcModeController(
            activity = this,
            service = pengWa.nfcService,
            diagnostics = pengWa.nfcDiagnostics,
            inspectRouting = BuildConfig.DEBUG,
        ) { tag ->
            runOnUiThread {
                val accepted = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    pengWa.exchangeMode.isActive(ExchangeMode.NfcRead)
                pengWa.nfcDiagnostics.record("Reader callback accepted=$accepted lifecycle=${lifecycle.currentState} role=${pengWa.exchangeMode.current} tech=${tag.techList.joinToString()}")
                if (accepted) onTagDiscovered(tag)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pengWa.nfcDiagnostics.record("Activity created restored=${savedInstanceState != null}")
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            requestedTab = tabFrom(intent)
        }
        handleNdefLaunch(intent)
        // 只有主页在前台时，才接收别人的卡；切走或退到后台立即关闭
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                pengWa.exchangeMode.mode.collect { mode -> nfcMode.apply(mode) }
            }
        }
        lifecycleScope.launch {
            pengWa.storageReady.await()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) BleBroadcastService.ensureRunning(this@MainActivity)
            setContent {
                PengWaTheme {
                    PengWaRoot(
                        requestedTab = requestedTab,
                        onTabRequestHandled = { requestedTab = null },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        pengWa.bleExchange.setAppForeground(true)
        // 后台广播的设置是开着的但服务没在跑（比如进程被杀后用户重新打开了应用）：补启动
        BleBroadcastService.ensureRunning(this)
    }

    override fun onResume() {
        super.onResume()
        pengWa.nfcDiagnostics.record("Activity resumed NFC=${nfcAdapter?.isEnabled} role=${pengWa.exchangeMode.current}")
    }

    override fun onStop() {
        super.onStop()
        pengWa.bleExchange.setAppForeground(false)
    }

    override fun onPause() {
        pengWa.nfcDiagnostics.record("Activity paused; releasing NFC role=${pengWa.exchangeMode.current}")
        nfcMode.apply(ExchangeMode.None)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tabFrom(intent)?.let { requestedTab = it }
        handleNdefLaunch(intent)
    }

    private fun handleNdefLaunch(intent: Intent) {
        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            pengWa.nfcDiagnostics.record("NDEF launch received")
        }
        val peer = NdefLaunchParser.peerFrom(intent) ?: run {
            if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
                pengWa.nfcDiagnostics.record("NDEF launch ignored: missing or invalid discovery payload")
            }
            return
        }
        lifecycleScope.launch {
            // 冷启动先加载本机身份，避免在存储初始化前发起交换。
            pengWa.storageReady.await()
            pengWa.bleExchange.discover(peer, initiate = true)
            // 完成消费后清空；等待存储时重建 Activity 仍可继续处理原始参数。
            intent.removeExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            pengWa.nfcDiagnostics.record("NDEF launch identified; starting BLE")
        }
    }

    private fun onTagDiscovered(tag: Tag) {
        lifecycleScope.launch(Dispatchers.IO) {
            nfcExchanger.exchange(tag)
            // 两部手机贴着不动时别反复触发，让系统在短时间内忽略这张"卡"
            runCatching { nfcAdapter?.ignore(tag, IGNORE_TAG_MILLIS, null, null) }
                .onSuccess { pengWa.nfcDiagnostics.record("Ignore tag debounceMs=$IGNORE_TAG_MILLIS accepted=$it") }
                .onFailure { pengWa.nfcDiagnostics.record("Ignore tag failed=${it.javaClass.simpleName}") }
        }
    }

    private fun tabFrom(intent: Intent?): Int? =
        intent?.takeIf { it.hasExtra(EXTRA_TAB) }?.getIntExtra(EXTRA_TAB, TAB_ME)

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_ME = 0
        const val TAB_CONTACTS = 1
        const val TAB_LAB = 2
        const val TAB_NEARBY = 3
        private const val IGNORE_TAG_MILLIS = 2000
    }
}
