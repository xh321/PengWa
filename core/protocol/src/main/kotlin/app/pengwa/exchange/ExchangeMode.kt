package app.pengwa.exchange

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 我的名片提供 NFC 出示／读取和二维码；主页与附近娃娃都启用蓝牙发现和卡片传输。
 * [None] 表示没有前台交换会话（集邮册、实验室、退后台、息屏、正在改名）。
 */
enum class ExchangeMode { None, NfcDisplay, NfcRead, Bluetooth }

/**
 * 当前交换方式的全应用共享状态。界面写入，NFC 服务、蓝牙总控、前台调度读取。
 * HCE 服务来自系统线程，所以读取用 [current]（volatile 语义由 StateFlow 保证）。
 */
class ExchangeModeState {
    private val _mode = MutableStateFlow(ExchangeMode.None)
    val mode: StateFlow<ExchangeMode> = _mode.asStateFlow()
    private var displayGeneration = 0L
    private var generation = 0L
    private var automaticOwner: Long? = null
    private var exchangeLease: Long? = null

    /** 当前出示会话编号；未出示时为 null。界面线程写入，HCE 可跨线程读取。 */
    @Volatile
    var nfcDisplaySession: Long? = null
        private set

    val current: ExchangeMode get() = _mode.value

    @Synchronized
    fun set(mode: ExchangeMode) {
        automaticOwner = null
        changeMode(mode)
    }

    private fun changeMode(mode: ExchangeMode, restart: Boolean = false) {
        if (_mode.value == mode && !restart) return
        exchangeLease = null
        nfcDisplaySession = if (mode == ExchangeMode.NfcDisplay) ++displayGeneration else null
        _mode.value = mode
    }

    @Synchronized
    internal fun beginAutomaticNfc(initial: ExchangeMode): Long {
        require(initial == ExchangeMode.NfcDisplay || initial == ExchangeMode.NfcRead)
        val owner = ++generation
        automaticOwner = owner
        changeMode(initial, restart = true)
        return owner
    }

    /** Only the current foreground session can switch, and never during an NFC transaction. */
    @Synchronized
    internal fun advanceAutomaticNfc(owner: Long): Boolean {
        if (automaticOwner != owner || exchangeLease != null) return false
        val next = when (current) {
            ExchangeMode.NfcDisplay -> ExchangeMode.NfcRead
            ExchangeMode.NfcRead -> ExchangeMode.NfcDisplay
            else -> return false
        }
        changeMode(next)
        return true
    }

    @Synchronized
    internal fun endAutomaticNfc(owner: Long) {
        if (automaticOwner != owner) return
        automaticOwner = null
        changeMode(ExchangeMode.None)
    }

    /** Reader and HCE callbacks acquire a lease before touching the transport. */
    @Synchronized
    fun beginNfcExchange(expected: ExchangeMode): Long? {
        if (expected != ExchangeMode.NfcRead && expected != ExchangeMode.NfcDisplay) return null
        if (current != expected || exchangeLease != null) return null
        return (++generation).also { exchangeLease = it }
    }

    @Synchronized
    fun isNfcExchangeActive(lease: Long): Boolean = exchangeLease == lease

    @Synchronized
    fun endNfcExchange(lease: Long) {
        if (exchangeLease == lease) exchangeLease = null
    }

    fun isActive(mode: ExchangeMode): Boolean = _mode.value == mode
}
