package app.pengwa.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
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

/** The draft choice belongs only to first-run onboarding; reopening the guide does not change it. */
@Composable
internal fun OnboardingAnalyticsOption(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
            Text(stringResource(R.string.analytics_onboarding_choice), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = { showExplanation = true }) { Text(stringResource(R.string.analytics_explanation)) }
    }
    if (showExplanation) AlertDialog(
        onDismissRequest = { showExplanation = false },
        title = { Text(stringResource(R.string.analytics_title)) },
        text = { Text(stringResource(R.string.analytics_description), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showExplanation = false }) { Text(stringResource(R.string.analytics_explanation_close)) } },
    )
}
