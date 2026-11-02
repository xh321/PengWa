package app.pengwa.ui

import android.content.Context
import android.nfc.NfcAdapter
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import app.pengwa.ui.components.PengWaIcon
import app.pengwa.ui.components.PengWaIcons
import app.pengwa.ui.theme.LocalStatusColors
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pengwa.R
import app.pengwa.BuildConfig

internal enum class NfcState { Unsupported, Disabled, Ready }

/** 钱包选择是兼容性建议，不是进入 NFC 会话的前置条件。 */
internal fun nfcStateFor(
    available: Boolean, enabled: Boolean,
): NfcState = when {
    !available -> NfcState.Unsupported
    !enabled -> NfcState.Disabled
    else -> NfcState.Ready
}

internal fun nfcState(context: Context): NfcState {
    val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcState.Unsupported
    return nfcStateFor(available = true, enabled = adapter.isEnabled)
}

@Composable
internal fun rememberNfcState(): NfcState {
    val context = LocalContext.current
    var state by remember(context) { mutableStateOf(nfcState(context)) }
    LifecycleResumeEffect(context) {
        state = nfcState(context)
        onPauseOrDispose { }
    }

    return state
}

@Composable
internal fun NfcStatus(
    state: NfcState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLab: (() -> Unit)? = null,
) {
    val taps = remember { DebugTapCounter() }
    val statusColors = LocalStatusColors.current
    val color = when (state) {
        NfcState.Ready -> statusColors.success
        NfcState.Disabled -> statusColors.warning
        NfcState.Unsupported -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = when (state) {
        NfcState.Ready -> statusColors.successContainer
        NfcState.Disabled -> statusColors.warningContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (BuildConfig.DEBUG && onOpenLab != null) {
                    IconButton(onClick = { if (taps.tap(SystemClock.elapsedRealtime())) onOpenLab() }) {
                        PengWaIcon(PengWaIcons.Tap, tint = color, contentDescription = stringResource(R.string.nfc_status_icon))
                    }
                } else {
                    PengWaIcon(PengWaIcons.Tap, tint = color)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(when (state) {
                            NfcState.Ready -> R.string.nfc_ready
                            NfcState.Disabled -> R.string.nfc_disabled
                            NfcState.Unsupported -> R.string.nfc_unsupported
                        }),
                        style = MaterialTheme.typography.titleMedium, color = color,
                    )
                    Text(
                        text = stringResource(when (state) {
                            NfcState.Ready -> R.string.nfc_auto_hint
                            NfcState.Disabled -> R.string.nfc_enable_hint
                            NfcState.Unsupported -> R.string.nfc_alternative_hint
                        }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (state == NfcState.Disabled) {
                TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.nfc_open_settings))
                }
            }
        }
    }
}
