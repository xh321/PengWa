package app.pengwa.analytics

import android.content.Context
import app.pengwa.BuildConfig
import app.pengwa.pengWa
import app.pengwa.identity.OnboardingStep
import app.pengwa.settings.BooleanSetting
import com.aptabase.Aptabase
import com.aptabase.InitOptions
import com.aptabase.TrackingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppAnalytics(context: Context) {
    private val context = context.applicationContext
    private val setting = BooleanSetting("usage_analytics")
    private val config = AnalyticsConfig(BuildConfig.APTABASE_APP_KEY, BuildConfig.APTABASE_HOST)
    val configured = config.valid
    private val _enabled = MutableStateFlow(setting.get(this.context))
    val enabled = _enabled.asStateFlow()
    @Suppress("unused")
    private val stopObserving = setting.observe(this.context) { _enabled.value = it }
    private var initialized = false
    private val analytics = Analytics(configured, { setting.get(this.context) && this.context.pengWa.identityRepository.onboardingStep() == OnboardingStep.Complete }) { event ->
        if (!initialized) {
            Aptabase.instance.initialize(this.context, config.appKey, InitOptions(
                host = config.host.trimEnd('/').takeIf { it.isNotEmpty() },
                trackingMode = if (BuildConfig.DEBUG) TrackingMode.asDebug else TrackingMode.asRelease,
            ))
            initialized = true
        }
        Aptabase.instance.trackEvent(event.eventName, event.properties)
    }

    suspend fun saveEnabled(value: Boolean) { setting.set(context, value) }

    fun setEnabled(value: Boolean) {
        context.pengWa.persistSetting { setting.set(context, value) }
    }

    fun track(event: AnalyticsEvent) = analytics.track(event)
}
