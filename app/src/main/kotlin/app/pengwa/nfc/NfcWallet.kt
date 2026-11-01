package app.pengwa.nfc

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.provider.Settings

/** 默认钱包可能影响厂商的 NFC 路由；只通过系统界面请求用户将本应用设为默认。 */
class NfcWallet(private val context: Context) {
    private val service = ComponentName(context, PengWaHostApduService::class.java)

    fun isDefault(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 35) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_WALLET)) {
                return@runCatching roles.isRoleHeld(RoleManager.ROLE_WALLET)
            }
        }
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return@runCatching false
        CardEmulation.getInstance(adapter)
            .isDefaultServiceForCategory(service, CardEmulation.CATEGORY_PAYMENT)
    }.getOrDefault(false)

    fun requestIntent(): Intent {
        if (Build.VERSION.SDK_INT >= 35) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_WALLET)) {
                return roles.createRequestRoleIntent(RoleManager.ROLE_WALLET)
            }
        }
        @Suppress("DEPRECATION")
        return Intent(CardEmulation.ACTION_CHANGE_DEFAULT)
            .putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT)
            .putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, service)
    }

    fun openSettings(): Boolean {
        // 部分厂商没有实现付款设置 Activity，逐级回退，始终由用户自行选择。
        for (action in listOf(Settings.ACTION_NFC_PAYMENT_SETTINGS, Settings.ACTION_NFC_SETTINGS,
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)) {
            try {
                context.startActivity(Intent(action))
                return true
            } catch (_: ActivityNotFoundException) {
                // 尝试下一个公开设置入口。
            } catch (_: SecurityException) {
                // 厂商设置页可能不向第三方应用开放。
            }
        }
        return false
    }
}
