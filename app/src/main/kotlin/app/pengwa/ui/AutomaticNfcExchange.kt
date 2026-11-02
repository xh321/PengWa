package app.pengwa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.pengwa.exchange.AutomaticNfcSession
import app.pengwa.exchange.ExchangeMode
import app.pengwa.pengWa
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun AutomaticNfcExchange(enabled: Boolean, nfcReady: Boolean, canDisplay: Boolean) {
    val state = LocalContext.current.pengWa.exchangeMode
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (!enabled || !nfcReady) {
        ExchangeSession(if (enabled) ExchangeMode.Bluetooth else ExchangeMode.None)
        return
    }
    LaunchedEffect(state, lifecycle, canDisplay) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val session = AutomaticNfcSession(state, canDisplay)
            try {
                if (!canDisplay) awaitCancellation()
                while (isActive) {
                    delay(session.nextDelayMillis())
                    session.advance()
                }
            } finally {
                session.close()
            }
        }
    }
}
