package app.pengwa.identity

import android.content.Context
import java.util.UUID

/**
 * 身份和有界用户卡片的本地存储，使用 SharedPreferences，不额外引入依赖。
 * 清除应用数据或卸载重装后会生成新的 GUID（清单里已关闭 allowBackup）。
 */
class IdentityRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("identity", Context.MODE_PRIVATE)

    /** 首次访问时生成并持久化，之后永远返回同一个值。 */
    val id: UUID by lazy {
        prefs.getString(KEY_ID, null)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID().also { prefs.edit().putString(KEY_ID, it.toString()).commit() }
    }

    /** 还没设置过显示名时返回 null，界面据此进入首次设置。 */
    fun load(): Identity? {
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val card = prefs.getString("card", null)?.let {
            runCatching { CardCodec.decode(java.util.Base64.getDecoder().decode(it))?.card }.getOrNull()
        } ?: CardContent()
        return Identity(id, name, card, cardComplete = true)
    }

    fun saveDisplayName(name: String) {
        prefs.edit().putString(KEY_NAME, name).apply()
    }

    fun saveCard(identity: Identity) {
        val encoded = java.util.Base64.getEncoder().encodeToString(CardCodec.encode(identity))
        check(prefs.edit().putString(KEY_NAME, identity.displayName).putString("card", encoded).commit())
    }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_NAME = "display_name"
    }
}
