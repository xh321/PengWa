package app.pengwa.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import app.pengwa.analytics.AnalyticsEvent
import app.pengwa.identity.OnboardingStep
import app.pengwa.identity.Identity
import app.pengwa.identity.NameError
import app.pengwa.identity.normalizeDisplayName
import app.pengwa.identity.validateDisplayName
import app.pengwa.notification.PengWaNotification
import app.pengwa.pengWa

class IdentityViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.pengWa.identityRepository
    private val analytics = application.pengWa.analytics

    var identity: Identity? by mutableStateOf(repository.load())
        private set

    var draft: Identity? by mutableStateOf(null)
        private set

    var onboardingStep by mutableStateOf(repository.onboardingStep())
        private set

    fun beginOnboardingEdit() {
        if (draft == null) draft = identity ?: Identity(repository.id, "")
    }

    suspend fun moveOnboardingTo(step: OnboardingStep): String? = viewModelScope.async { runCatching {
        repository.saveOnboardingStep(step)
        onboardingStep = step
        null
    }.getOrElse { "进度保存失败，请重试" } }.await()

    /** Persist the final choice before publishing completion, so a failed write keeps the guide open. */
    suspend fun finishOnboarding(shareUsage: Boolean): String? = viewModelScope.async {
        runCatching {
            getApplication<Application>().pengWa.analytics.saveEnabled(shareUsage)
            repository.saveOnboardingStep(OnboardingStep.Complete)
            onboardingStep = OnboardingStep.Complete
            null
        }.getOrElse { "选择保存失败，请重试" }
    }.await()

    suspend fun saveOnboardingDraft(): String? = saveDraft(onboarding = true)

    fun beginEdit() { draft = identity }
    fun updateDraft(value: Identity) { draft = value }
    suspend fun saveDraft(): String? = saveDraft(onboarding = false)

    private suspend fun saveDraft(onboarding: Boolean): String? =
        viewModelScope.async { persistDraft(onboarding) }.await()

    private suspend fun persistDraft(onboarding: Boolean): String? {
        val value = draft ?: return "卡片尚未准备好"
        validateDisplayName(value.displayName)?.let { return "显示名为空、过长或包含不可显示字符（最多 32 字节）" }
        val normalized = value.copy(displayName = normalizeDisplayName(value.displayName))
        app.pengwa.identity.CardCodec.validate(normalized.card)?.let { return it }
        val creating = identity == null
        return runCatching {
            repository.saveCard(normalized, onboardingStep = if (onboarding) OnboardingStep.PreviewCard else null)
            identity = repository.load()
            getApplication<Application>().pengWa.bleExchange.refresh()
            if (onboarding) onboardingStep = OnboardingStep.PreviewCard
            PengWaNotification.refresh(getApplication())
            if (creating) analytics.track(AnalyticsEvent.CardCreated)
            else if (!onboarding) analytics.track(AnalyticsEvent.CardEditSaved)
            null
        }.getOrElse { it.message ?: "保存失败，请重试" }
    }

    /** 保存显示名；通过校验返回 null，否则返回错误原因且不保存。存的是规范化后的名字。 */
    suspend fun saveDisplayName(raw: String): NameError? {
        validateDisplayName(raw)?.let { return it }
        val creating = identity == null
        repository.saveDisplayName(normalizeDisplayName(raw))
        identity = repository.load()
        PengWaNotification.refresh(getApplication()) // 通知栏上的名字同步更新
        if (creating) analytics.track(AnalyticsEvent.CardCreated)
        return null
    }
}
