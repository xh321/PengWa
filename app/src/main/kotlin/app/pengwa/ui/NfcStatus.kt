package app.pengwa.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import app.pengwa.ui.components.PengWaIcon
import app.pengwa.ui.components.PengWaIcons
import app.pengwa.ui.theme.LocalStatusColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pengwa.R
import app.pengwa.BuildConfig

internal enum class NfcState { Unsupported, Disabled, Ready }

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

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                state = nfcState(context)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

@Composable
internal fun NfcStatus(
    state: NfcState,
    onOpenSettings: () -> Unit,
    configured: Boolean,
    loadingCard: Boolean,
    modifier: Modifier = Modifier,
    onOpenLab: (() -> Unit)? = null,
) {
    val taps = remember { DebugTapCounter() }
    val statusColors = LocalStatusColors.current
    val color = when (state) {
        NfcState.Ready -> if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        NfcState.Disabled -> statusColors.warning
        NfcState.Unsupported -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (BuildConfig.DEBUG && onOpenLab != null) {
            IconButton(
                onClick = { if (taps.tap(SystemClock.elapsedRealtime())) onOpenLab() },
                modifier = Modifier.size(56.dp),
            ) {
                PengWaIcon(PengWaIcons.Tap, Modifier.size(32.dp), stringResource(R.string.nfc_status_icon), tint = color)
            }
        } else {
            PengWaIcon(PengWaIcons.Tap, Modifier.size(32.dp), tint = color)
        }
        Text(
            text = stringResource(when (state) {
                NfcState.Ready -> R.string.nfc_ready
                NfcState.Disabled -> R.string.nfc_disabled
                NfcState.Unsupported -> R.string.nfc_unsupported
            }),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(when {
                state == NfcState.Disabled -> R.string.nfc_enable_hint
                state == NfcState.Unsupported -> R.string.nfc_alternative_hint
                !configured -> R.string.nfc_unconfigured
                loadingCard -> R.string.nfc_loading_card
                else -> R.string.nfc_auto_hint
            }),
            modifier = Modifier.widthIn(max = 340.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (state == NfcState.Disabled) {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.nfc_open_settings)) }
        }
    }
}
