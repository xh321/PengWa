package app.pengwa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.pengwa.ble.BleBroadcastService
import app.pengwa.exchange.ExchangeMode
import app.pengwa.ui.PengWaRoot
import app.pengwa.ui.theme.PengWaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** 通知栏按钮带来的"请切到某一页"请求；界面处理完后清空。 */
    private var requestedTab by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pengWa.nfcDiagnostics.record("Activity created restored=${savedInstanceState != null}")
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            requestedTab = tabFrom(intent)
        }
        lifecycleScope.launch {
            pengWa.storageReady.await()
            if (savedInstanceState == null) pengWa.analytics.track(app.pengwa.analytics.AnalyticsEvent.AppStarted)
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

    override fun onStop() {
        super.onStop()
        pengWa.bleExchange.setAppForeground(false)
    }

    override fun onPause() {
        pengWa.nfcDiagnostics.record("Activity paused; releasing foreground exchange")
        pengWa.exchangeMode.set(ExchangeMode.None)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tabFrom(intent)?.let { requestedTab = it }
    }

    private fun tabFrom(intent: Intent?): Int? =
        intent?.takeIf { it.hasExtra(EXTRA_TAB) }?.getIntExtra(EXTRA_TAB, TAB_ME)

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_ME = 0
        const val TAB_CONTACTS = 1
        const val TAB_LAB = 2
        const val TAB_NEARBY = 3
    }
}
