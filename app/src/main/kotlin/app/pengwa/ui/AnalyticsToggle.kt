package app.pengwa.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pengwa.R
import app.pengwa.pengWa
import app.pengwa.ui.components.SettingsRow

@Composable
internal fun AnalyticsToggle() {
    val context = LocalContext.current
    val analytics = remember { context.pengWa.analytics }
    val enabled by analytics.enabled.collectAsStateWithLifecycle()
    AnalyticsSettingContent(enabled, analytics.configured, analytics::setEnabled)
}

@Composable
internal fun AnalyticsSettingContent(checked: Boolean, configured: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column {
        SettingsRow(
            title = stringResource(R.string.analytics_title),
            description = stringResource(R.string.analytics_description),
            checked = checked,
            enabled = configured || checked,
            onCheckedChange = onCheckedChange,
        )
        if (!configured) Text(stringResource(R.string.analytics_unconfigured), style = MaterialTheme.typography.bodySmall)
    }
}
