package app.pengwa.ble

import android.content.Context
import app.pengwa.settings.BooleanSetting

/**
 * "后台蓝牙广播（常开蓝牙）"的用户设置。默认关闭。
 * 打开后由应用的前台服务在后台持续广播，别人扫到后可以向我申请交换，同意后才会交换。
 */
object BroadcastSettings {
    private val setting = BooleanSetting("background_broadcast")

    fun isEnabled(context: Context): Boolean = setting.get(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        setting.set(context, enabled)
    }

    /** 监听开关变化，返回取消监听的函数（SharedPreferences 只弱引用监听器，调用方要保留它）。 */
    fun observe(context: Context, onChange: (Boolean) -> Unit): () -> Unit =
        setting.observe(context, onChange)
}
