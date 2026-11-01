package app.pengwa.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pengwa.R
import app.pengwa.pengWa

/**
 * 仅 Debug 版可通过 NFC 状态图标连点五次进入。日志在进程内保留，切换到本页不会清除。
 */
@Composable
fun LabScreen(
    modifier: Modifier = Modifier,
    contactsViewModel: ContactsViewModel = viewModel(),
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val controller = remember { context.pengWa.bleExchange }
    val running by controller.running.collectAsStateWithLifecycle()
    val bluetoothOn by controller.bluetoothOn.collectAsStateWithLifecycle()
    val log by controller.logLines.collectAsStateWithLifecycle()
    val diagnostics = remember { context.pengWa.nfcDiagnostics }
    val nfcLog by diagnostics.lines.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.lab_title), style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onClose) { Text(stringResource(R.string.lab_back)) }

        Text(stringResource(R.string.lab_nfc), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.lab_nfc_help), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("PengWa NFC diagnostics", diagnostics.report()))
            }) { Text(stringResource(R.string.lab_nfc_copy)) }
            TextButton(onClick = diagnostics::clear) { Text(stringResource(R.string.lab_nfc_clear)) }
        }
        Text(
            text = nfcLog.takeLast(60).joinToString("\n").ifEmpty { stringResource(R.string.ble_log_empty) },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Text(stringResource(R.string.lab_ble), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(
                R.string.lab_ble_status,
                stringResource(if (running) R.string.lab_running else R.string.lab_stopped),
                stringResource(if (bluetoothOn) R.string.lab_on else R.string.lab_off),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = log.take(40).joinToString("\n").ifEmpty { stringResource(R.string.ble_log_empty) },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Text(stringResource(R.string.lab_demo_data), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = contactsViewModel::addDemoContacts) {
            Text(stringResource(R.string.lab_add_demo))
        }
        // 点完可以按 Home 退到后台，5 秒后会收到一条带"同意 / 拒绝"按钮的通知，验证后台提醒
        OutlinedButton(onClick = { controller.debugSimulateIncomingRequest("模拟小熊", 5_000) }) {
            Text(stringResource(R.string.lab_simulate_request))
        }
    }
}
