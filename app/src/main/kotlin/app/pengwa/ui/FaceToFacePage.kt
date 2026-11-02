package app.pengwa.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import app.pengwa.ui.theme.LocalQrColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pengwa.R
import app.pengwa.identity.Identity
import app.pengwa.pengWa
import app.pengwa.nfc.NfcWallet
import app.pengwa.qr.QrEncoder
import app.pengwa.qr.QrPayload

/**
 * NFC 与二维码共用的面对面交换页，扫一扫一键打开相机。
 * 离开主页时组合销毁，相机与亮度覆盖随之释放。
 */
@Composable
fun FaceToFacePage(
    identity: Identity,
    onMessage: (String) -> Unit,
    scanning: Boolean,
    onScanningChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLab: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    BackHandler(enabled = scanning) { onScanningChange(false) }

    val nfcState = rememberNfcState()
    val canDisplay = remember(context) { context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_NFC_HOST_CARD_EMULATION) }
    val wallet = remember(context) { NfcWallet(context, context.pengWa.nfcService) }
    var defaultWallet by remember(wallet) { mutableStateOf(wallet.isDefault()) }
    LifecycleResumeEffect(wallet) {
        defaultWallet = wallet.isDefault()
        onPauseOrDispose { }
    }
    val walletLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        defaultWallet = wallet.isDefault()
    }
    var walletPromptDismissed by rememberSaveable { mutableStateOf(false) }
    AutomaticNfcExchange(enabled = !scanning, nfcReady = nfcState == NfcState.Ready, canDisplay = canDisplay)

    BrightnessLock(enabled = !scanning)

    if (scanning) {
        QrScanner(
            onResult = { text ->
                onScanningChange(false)
                val peer = QrPayload.decode(text)
                when {
                    peer == null -> onMessage(resources.getString(R.string.qr_invalid))
                    peer.id == identity.id -> onMessage("这是自己的二维码")
                    else -> context.pengWa.bleExchange.discover(peer, initiate = true, source = "二维码")
                }
            },
            onClose = { onScanningChange(false) },
            modifier = modifier,
        )
        return
    }

    FaceToFacePageContent(
        identity = identity,
        nfcState = nfcState,
        onOpenNfcSettings = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
        defaultWallet = defaultWallet || !canDisplay,
        showWalletPrompt = !walletPromptDismissed,
        onDismissWalletPrompt = { walletPromptDismissed = true },
        onRequestWallet = {
            walletPromptDismissed = true
            runCatching { walletLauncher.launch(wallet.requestIntent()) }
                .onFailure {
                    if (!wallet.openSettings()) onMessage(resources.getString(R.string.nfc_wallet_open_failed))
                }
        },
        onOpenLab = onOpenLab,
        modifier = modifier,
    )
}

@Composable
internal fun FaceToFacePageContent(
    identity: Identity,
    nfcState: NfcState,
    onOpenNfcSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLab: (() -> Unit)? = null,
    defaultWallet: Boolean = false,
    onRequestWallet: () -> Unit = {},
    showWalletPrompt: Boolean = false,
    onDismissWalletPrompt: () -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 300.dp && maxWidth >= 480.dp
        val codeSize = minOf(240.dp, maxHeight - 40.dp).coerceAtLeast(48.dp)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ExchangeQrCard(identity, onExpand = { expanded = true }, showHint = !compact, codeSize = codeSize) {
                NfcStatus(nfcState, onOpenNfcSettings, onOpenLab = onOpenLab)
            }
            NfcWalletInstructions(nfcState, defaultWallet, onRequestWallet)
        }
    }
    if (expanded) QrCardDialog(identity) { expanded = false }
    if (showWalletPrompt && nfcState == NfcState.Ready && !defaultWallet) {
        AlertDialog(
            onDismissRequest = onDismissWalletPrompt,
            title = { Text(stringResource(R.string.nfc_wallet_set_default)) },
            text = { Text(stringResource(R.string.nfc_wallet_help)) },
            confirmButton = {
                TextButton(onClick = onRequestWallet) { Text(stringResource(R.string.nfc_wallet_set_default)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissWalletPrompt) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun NfcWalletInstructions(nfcState: NfcState, defaultWallet: Boolean, onRequestWallet: () -> Unit) {
    if (nfcState == NfcState.Ready && !defaultWallet) WalletHelp(onRequestWallet)
}

/** 所有品牌均可使用；提示兼容问题，不依据品牌或默认钱包状态阻止出示。 */
@Composable
private fun WalletHelp(onRequestWallet: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.nfc_wallet_help_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.nfc_wallet_help), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRequestWallet, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.nfc_wallet_set_default))
            }
        }
    }
}

@Composable
private fun ExchangeQrCard(
    identity: Identity,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    showHint: Boolean = true,
    codeSize: Dp = 240.dp,
    footer: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(if (showHint) 16.dp else 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onExpand),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QrCodeView(remember(identity) { QrPayload.encode(identity) }, if (showHint) Modifier.widthIn(max = 240.dp).fillMaxWidth() else Modifier.size(codeSize))
                if (showHint) Text(stringResource(R.string.qr_hint), modifier = Modifier.widthIn(max = 340.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (showHint) {
                Row(
                    Modifier.widthIn(max = 240.dp).fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.exchange_or), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            footer()
        }
    }
}

@Composable
private fun QrCardDialog(identity: Identity, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), horizontalAlignment = Alignment.End) {
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.qr_close))
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val edge = minOf(maxWidth, maxHeight)
                    QrCodeView(remember(identity) { QrPayload.encode(identity) }, Modifier.size(edge))
                }
            }
        }
    }
}

/** 深色码点配浅色底，夜间使用柔和底色；保留静区与模块比例。 */
@Composable
fun QrCodeView(text: String, modifier: Modifier = Modifier) {
    val colors = LocalQrColors.current
    val bitmap = remember(text, colors) {
        val matrix = QrEncoder.encode(text)
        // Four light modules are the quiet zone; use the same color as the light cells.
        val quietZone = 4
        val edge = matrix.width + quietZone * 2
        val foreground = colors.foreground.toArgb()
        val background = colors.background.toArgb()
        val pixels = IntArray(edge * edge) { index ->
            val x = index % edge - quietZone
            val y = index / edge - quietZone
            if (x in 0 until matrix.width && y in 0 until matrix.height && matrix[x, y])
                foreground else background
        }
        Bitmap.createBitmap(pixels, edge, edge, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.qr_code_label),
        filterQuality = FilterQuality.None,
        modifier = modifier.aspectRatio(1f),
    )
}
