package app.pengwa

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import app.pengwa.ble.BleExchangeController
import app.pengwa.contacts.ContactStore
import app.pengwa.exchange.ExchangeMode
import app.pengwa.exchange.ExchangeModeState
import app.pengwa.identity.IdentityRepository
import app.pengwa.meeting.MeetingRecorder
import app.pengwa.nfc.NfcDiagnosticLog
import app.pengwa.notification.PengWaNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 全应用共享的单例都挂在这里。身份、集邮册、相遇记录器各只有一份，
 * 避免多个入口（界面、蓝牙、NFC 服务、通知）各建一份造成首次启动时 GUID 生成竞争。
 */
class PengWaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val identityRepository: IdentityRepository by lazy { IdentityRepository(this) }
    val contactStore: ContactStore by lazy { ContactStore(this) }
    val meetings: MeetingRecorder by lazy {
        MeetingRecorder(contactStore, selfId = { identityRepository.id }, scope = applicationScope,
            onStored = { bleExchange.onCardStored(it) })
    }

    /**
     * 当前前台目的地（我的名片 / 附近娃娃 / 都不是）。我的名片同时提供 NFC 与扫码。
     * NFC 服务、蓝牙总控、NFC 前台调度都读它。
     */
    val exchangeMode = ExchangeModeState()

    val nfcDiagnostics by lazy {
        NfcDiagnosticLog(
            enabled = BuildConfig.DEBUG,
            header = "PengWa NFC diagnostics\n" +
                "App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "Build: ${BuildConfig.BUILD_REVISION}\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}\n" +
                "System: ${Build.DISPLAY}\n" +
                "Logs: oldest first; last ${NfcDiagnosticLog.MAX_LINES} events; cleared when process exits.",
            output = { Log.d("PengWaNfc", it) },
        )
    }

    /**
     * 蓝牙交换总控：停在主页或附近娃娃时广播并扫描；设置里打开"后台蓝牙广播"后，由前台服务在后台只广播、等别人来申请。
     * 被申请的一方同意后才会交换。
     */
    val bleExchange: BleExchangeController by lazy {
        BleExchangeController(
            context = this,
            identityProvider = { identityRepository.load() },
            onPeer = { peer, how -> meetings.record(peer, "蓝牙：$how") },
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 进程被系统杀掉后重新启动时，恢复用户开启的常驻通知
        PengWaNotification.refresh(this)
        // 主页与附近娃娃启用前台蓝牙；离开前台交换会话就停
        mainScope.launch {
            exchangeMode.mode.collect { bleExchange.setPageActive(it == ExchangeMode.Bluetooth || it == ExchangeMode.NfcDisplay || it == ExchangeMode.NfcRead) }
        }
    }
}

/** 任意 Context 取到应用单例。 */
val Context.pengWa: PengWaApplication
    get() = applicationContext as PengWaApplication
