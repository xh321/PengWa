package app.pengwa.analytics

import android.content.Context
import app.pengwa.BuildConfig
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
    private var initialized = false
    private val analytics = Analytics(configured, { _enabled.value }) { event ->
        if (!initialized) {
            Aptabase.instance.initialize(this.context, config.appKey, InitOptions(
                host = config.host.trimEnd('/').takeIf { it.isNotEmpty() },
                trackingMode = if (BuildConfig.DEBUG) TrackingMode.asDebug else TrackingMode.asRelease,
            ))
            initialized = true
        }
        Aptabase.instance.trackEvent(event.eventName, event.properties)
    }

    fun setEnabled(value: Boolean) = analytics.changeConsent {
        setting.set(context, value)
        _enabled.value = value
    }

    fun track(event: AnalyticsEvent) = analytics.track(event)
}
