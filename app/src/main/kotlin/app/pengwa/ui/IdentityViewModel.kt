package app.pengwa.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import app.pengwa.identity.Identity
import app.pengwa.identity.NameError
import app.pengwa.identity.normalizeDisplayName
import app.pengwa.identity.validateDisplayName
import app.pengwa.notification.PengWaNotification
import app.pengwa.pengWa

class IdentityViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.pengWa.identityRepository

    var identity: Identity? by mutableStateOf(repository.load())
        private set

    var draft: Identity? by mutableStateOf(null)
        private set

    fun beginEdit() { draft = identity }
    fun updateDraft(value: Identity) { draft = value }
    fun saveDraft(): String? {
        val value = draft ?: return "卡片尚未准备好"
        validateDisplayName(value.displayName)?.let { return "显示名为空、过长或包含不可显示字符（最多 32 字节）" }
        val normalized = value.copy(displayName = normalizeDisplayName(value.displayName))
        app.pengwa.identity.CardCodec.validate(normalized.card)?.let { return it }
        return runCatching {
            repository.saveCard(normalized)
            identity = repository.load()
            PengWaNotification.refresh(getApplication())
            null
        }.getOrElse { it.message ?: "保存失败，请重试" }
    }

    /** 保存显示名；通过校验返回 null，否则返回错误原因且不保存。存的是规范化后的显示名。 */
    fun saveDisplayName(raw: String): NameError? {
        validateDisplayName(raw)?.let { return it }
        repository.saveDisplayName(normalizeDisplayName(raw))
        identity = repository.load()
        PengWaNotification.refresh(getApplication()) // 通知栏上的显示名同步更新
        return null
    }
}
