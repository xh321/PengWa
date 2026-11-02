package app.pengwa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pengwa.R
import app.pengwa.analytics.AnalyticsEvent
import app.pengwa.pengWa

/** Settings is a detail destination with the same back behavior as the card pages. */
@Composable
internal fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        context.pengWa.analytics.track(AnalyticsEvent.SettingsViewed)
    }
    SettingsPage(onBack, modifier) {
        SettingsContent(
            backgroundBroadcast = { BackgroundBroadcastToggle() },
            notification = { NotificationToggle() },
            update = { AppUpdateSection() },
            analytics = { AnalyticsToggle() },
        )
    }
}

@Composable
internal fun SettingsPage(onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CardDetailsPage(stringResource(R.string.settings_title), onBack, modifier) { content() }
}

@Composable
internal fun SettingsContent(
    backgroundBroadcast: @Composable () -> Unit,
    notification: @Composable () -> Unit,
    update: @Composable () -> Unit = {},
    analytics: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) { backgroundBroadcast() }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) { notification() }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) { analytics() }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) { update() }
        }
    }
}
