package app.pengwa.ui

import android.content.Context
import android.nfc.NfcAdapter
import android.content.pm.PackageManager
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
import app.pengwa.exchange.ExchangeMode

internal enum class NfcState { Unsupported, Disabled, DisplayUnsupported, Ready }

/** 钱包选择是兼容性建议，不是进入 NFC 会话的前置条件。 */
internal fun nfcStateFor(
    role: ExchangeMode, available: Boolean, enabled: Boolean, hce: Boolean,
): NfcState = when {
    !available -> NfcState.Unsupported
    !enabled -> NfcState.Disabled
    role == ExchangeMode.NfcDisplay && !hce -> NfcState.DisplayUnsupported
    else -> NfcState.Ready
}

internal fun nfcState(context: Context, role: ExchangeMode): NfcState {
    val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcState.Unsupported
    val hce = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
    return nfcStateFor(role, available = true, enabled = adapter.isEnabled, hce = hce)
}

@Composable
internal fun rememberNfcState(role: ExchangeMode): NfcState {
    val context = LocalContext.current
    var state by remember(role) { mutableStateOf(nfcState(context, role)) }
    LifecycleResumeEffect(role) {
        state = nfcState(context, role)
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
        NfcState.Unsupported, NfcState.DisplayUnsupported -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier.widthIn(max = 340.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (BuildConfig.DEBUG && onOpenLab != null) {
            IconButton(onClick = { if (taps.tap(SystemClock.elapsedRealtime())) onOpenLab() }) {
                PengWaIcon(PengWaIcons.Tap, tint = color, contentDescription = stringResource(R.string.nfc_status_icon))
            }
        } else {
            PengWaIcon(PengWaIcons.Tap, tint = color)
        }
        Text(
            text = stringResource(when (state) {
                NfcState.Ready -> R.string.nfc_ready
                NfcState.Disabled -> R.string.nfc_disabled
                NfcState.Unsupported -> R.string.nfc_unsupported
                NfcState.DisplayUnsupported -> R.string.nfc_display_unsupported
            }),
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium, color = color,
        )
        if (state == NfcState.Disabled) {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.nfc_open_settings)) }
        }
    }
}
