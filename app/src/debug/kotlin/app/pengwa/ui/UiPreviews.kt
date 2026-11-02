package app.pengwa.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import app.pengwa.MainActivity
import app.pengwa.R
import androidx.compose.ui.res.stringResource
import app.pengwa.ui.components.SettingsRow
import app.pengwa.ui.components.ExchangeRequestDialog
import app.pengwa.ui.components.MeetingCelebrationOverlay
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import app.pengwa.ui.theme.ContentMaxWidth
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.pengwa.ble.NearbyPeer
import app.pengwa.ble.PeerState
import app.pengwa.contacts.Contact
import app.pengwa.identity.Identity
import app.pengwa.ui.theme.PengWaTheme
import java.util.UUID

@Preview(name = "浅色", widthDp = 360, heightDp = 720, locale = "zh-rCN")
@Preview(name = "深色", widthDp = 360, heightDp = 720, locale = "zh-rCN", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "窄屏大字体", widthDp = 320, heightDp = 640, locale = "zh-rCN", fontScale = 1.5f)
internal annotation class PengWaPreviews

@Composable
internal fun PreviewSurface(content: @Composable () -> Unit) {
    PengWaTheme(dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}

internal data class NfcPreviewState(val name: String, val state: NfcState, val defaultWallet: Boolean = false)

internal class NfcPreviewProvider : PreviewParameterProvider<NfcPreviewState> {
    override val values = sequenceOf(
        NfcPreviewState("就绪 · 钱包提示", NfcState.Ready),
        NfcPreviewState("就绪 · 已是默认钱包", NfcState.Ready, defaultWallet = true),
        NfcPreviewState("仅支持读取", NfcState.Ready, defaultWallet = true),
        NfcPreviewState("NFC 未开启", NfcState.Disabled),
        NfcPreviewState("不支持 NFC", NfcState.Unsupported),
        NfcPreviewState("无 HCE 自动保持读取", NfcState.Ready, defaultWallet = true),
    )

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@PengWaPreviews
@Composable
internal fun FaceToFacePagePreview(@PreviewParameter(NfcPreviewProvider::class) sample: NfcPreviewState) {
    var walletPromptDismissed by remember(sample) { mutableStateOf(false) }
    PreviewHome {
        FaceToFacePageContent(
            identity = Identity(UUID(0L, 1L), "小棉花"),
            nfcState = sample.state,
            defaultWallet = sample.defaultWallet,
            showWalletPrompt = !walletPromptDismissed,
            onDismissWalletPrompt = { walletPromptDismissed = true },
            onRequestWallet = { walletPromptDismissed = true },
            onOpenNfcSettings = {},
        )
    }
}

internal data class BluetoothPreviewState(
    val name: String,
    val permitted: Boolean = true,
    val denied: Boolean = false,
    val bluetoothOn: Boolean = true,
    val nearby: List<NearbyPeer> = emptyList(),
)

internal class BluetoothPreviewProvider : PreviewParameterProvider<BluetoothPreviewState> {
    override val values = sequenceOf(
        BluetoothPreviewState("尚未授权", permitted = false),
        BluetoothPreviewState("权限被拒绝", permitted = false, denied = true),
        BluetoothPreviewState("蓝牙关闭", bluetoothOn = false),
        BluetoothPreviewState("搜索中"),
        BluetoothPreviewState("附近的人与交换状态", nearby = previewPeers),
        BluetoothPreviewState("信号与距离参考", nearby = signalPreviewPeers),
        BluetoothPreviewState("信号与交换状态", nearby = signalPreviewPeers.mapIndexed { index, peer ->
            peer.copy(
                name = if (index == 0) "名字很长很长很长的测试娃友" else peer.name,
                rssi = if (index == 0) 127 else peer.rssi,
                state = listOf(PeerState.Waiting, PeerState.Failed, PeerState.Done)[index],
                message = if (index == 1) "对方暂时没有回应，请稍后重试" else null,
            )
        }),
    )

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@PengWaPreviews
@Composable
internal fun BluetoothPagePreview(@PreviewParameter(BluetoothPreviewProvider::class) sample: BluetoothPreviewState) {
    PreviewDestination(MainActivity.TAB_NEARBY) {
        BluetoothPageContent(
            permitted = sample.permitted,
            denied = sample.denied,
            bluetoothOn = sample.bluetoothOn,
            nearby = sample.nearby,
            onPermission = {},
            onEnableBluetooth = {},
            onExchange = {},
        )
    }
}

private val previewPeers = PeerState.entries.mapIndexed { index, state ->
    NearbyPeer(
        address = "02:00:00:00:00:0$index",
        name = if (state == PeerState.Discovered) null else "名字很长很长的娃友",
        rssi = -55 - index * 8,
        lastSeenMillis = 0L,
        state = state,
        message = if (state == PeerState.Failed) "对方暂时没有回应，请稍后重试" else null,
    )
}

private val signalPreviewPeers = listOf(-48, -68, -88).mapIndexed { index, rssi ->
    NearbyPeer(
        address = "02:00:00:00:01:0$index",
        name = listOf("测试小熊", "测试小兔", "测试小鹿")[index],
        rssi = rssi,
        lastSeenMillis = 0L,
        state = PeerState.Ready,
    )
}

internal data class ContactsPreviewState(val name: String, val contacts: List<Contact>)

internal class ContactsPreviewProvider : PreviewParameterProvider<ContactsPreviewState> {
    override val values = sequenceOf(
        ContactsPreviewState("空集邮册", emptyList()),
        ContactsPreviewState("长名字与相遇次数", previewContacts.take(3)),
        ContactsPreviewState("可滚动的集邮册", previewContacts),
    )

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@PengWaPreviews
@Composable
internal fun ContactsScreenPreview(@PreviewParameter(ContactsPreviewProvider::class) sample: ContactsPreviewState) {
    PreviewDestination(MainActivity.TAB_CONTACTS) {
        ContactsScreenContent(contacts = sample.contacts, now = 1_793_462_400_000L) { buttonModifier ->
            CameraButtonContent(onClick = {}, modifier = buttonModifier)
        }
    }
}

private val previewContacts = List(20) { index ->
    Contact(
        id = UUID(0L, index.toLong() + 1),
        displayName = when (index) {
            0 -> "小棉花"
            1 -> "名字很长很长的娃友"
            2 -> "🧸 棉花糖"
            else -> "娃友 ${index + 1}"
        },
        firstMetAt = 1_793_462_400_000L,
        lastMetAt = 1_793_462_400_000L + index * 3_600_000L,
        metCount = if (index == 1) 999 else index + 1,
    )
}

@Composable
internal fun PreviewDestination(tab: Int, content: @Composable () -> Unit) {
    PreviewSurface {
        PengWaScaffold(tab, onSelect = {}) { modifier ->
            Box(modifier) { content() }
        }
    }
}

@Composable
internal fun PreviewHome(content: @Composable () -> Unit) {
    PreviewDestination(MainActivity.TAB_ME) {
        Column(Modifier.fillMaxSize()) {
            HomeHeader(Identity(UUID(0L, 1L), "小棉花"), onView = {}, onScan = {}, onSettings = {})
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@PengWaPreviews
@Composable
internal fun SettingsPreview(
    updateState: app.pengwa.update.UpdateState = app.pengwa.update.UpdateState(),
    analyticsConfigured: Boolean = false,
    analyticsInitiallyEnabled: Boolean = false,
) {
    var analyticsEnabled by remember(analyticsInitiallyEnabled) { mutableStateOf(analyticsInitiallyEnabled) }
    PreviewSurface {
        SettingsPage(onBack = {}, modifier = Modifier.safeDrawingPadding()) {
            SettingsContent(
                onShowGuide = {},
                backgroundBroadcast = { SettingsRow(stringResource(R.string.bg_setting_title), stringResource(R.string.bg_setting_desc), false) {} },
                notification = { SettingsRow(stringResource(R.string.notif_setting_title), stringResource(R.string.notif_setting_desc), false) {} },
                update = { AppUpdateContent(updateState, {}, {}, {}) },
                analytics = { AnalyticsSettingContent(analyticsEnabled, analyticsConfigured) { analyticsEnabled = it } },
            )
        }
    }
}

@PengWaPreviews
@Composable
internal fun RequestPreview() {
    PreviewHome { }
    PengWaTheme(dynamicColor = false) { ExchangeRequestDialog("名字很长很长的娃友", "00000001", {}, {}) }
}

@PengWaPreviews
@Composable
internal fun ConnectionSuccessPreview() {
    PreviewHome { }
    PengWaTheme(dynamicColor = false) {
        MeetingCelebrationOverlay(name = "名字很长很长的娃友", visible = true, onDismiss = {})
    }
}

@PengWaPreviews
@Composable
internal fun NameFormPreview() {
    PreviewSurface { NameForm("", false, { null }, {}) }
}

@PengWaPreviews
@Composable
internal fun ScannerPermissionPreview() {
    PreviewSurface { Box(Modifier.fillMaxSize().background(Color.Black)) { ScannerOverlay(false, true, {}, {}) } }
}

@PengWaPreviews
@Composable
internal fun ContactDetailsPreview() {
    PreviewSurface { ContactDetails(previewContacts[1]) {} }
}


@PengWaPreviews
@Composable
internal fun HomeBluetoothNoticePreview() {
    PreviewHome {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeBluetoothNotice("交换卡片需要蓝牙授权") { TextButton(onClick = {}) { Text("授权蓝牙", maxLines = 1) } }
            HomeBluetoothNotice("交换卡片需要蓝牙授权") { TextButton(onClick = {}) { Text("前往设置", maxLines = 1) } }
            HomeBluetoothNotice("请开启蓝牙以交换卡片") { TextButton(onClick = {}) { Text("开启蓝牙", maxLines = 1) } }
            HomeBluetoothNotice("连接已断开，请保持双方应用打开后重试") { TextButton(onClick = {}) { Text("重试连接", maxLines = 1) } }
            HomeBluetoothNotice("正在通过蓝牙交换卡片")
        }
    }
}

/** Fixed test data for running-emulator update screenshots; no network or installation actions. */
internal fun updatePreviewState(index: Int): app.pengwa.update.UpdateState {
    val release = app.pengwa.update.UpdateRelease("app.pengwa.debug", 200, "0.2.0-nightly.2", 29,
        "https://github.com/xh321/PengWa/releases/download/nightly/pengwa.apk", 17_000_000, "a".repeat(64),
        "改进名片交换体验，修复已知问题。")
    return when (index) {
        1 -> app.pengwa.update.UpdateState(app.pengwa.update.UpdateStage.Checking)
        2 -> app.pengwa.update.UpdateState(app.pengwa.update.UpdateStage.Available, release)
        3 -> app.pengwa.update.UpdateState(app.pengwa.update.UpdateStage.Downloading, release, progress = 0.46f, activeSource = "国内加速 · gh-proxy.com", downloadedBytes = 7_820_000, bytesPerSecond = 430_000)
        4 -> app.pengwa.update.UpdateState(app.pengwa.update.UpdateStage.Ready, release)
        5 -> app.pengwa.update.UpdateState(app.pengwa.update.UpdateStage.Failed, error = "安装包签名与当前应用不同，无法保留数据覆盖更新。请联系发布者使用固定签名")
        6 -> app.pengwa.update.UpdateState(app.pengwa.update.UpdateStage.Current)
        7 -> app.pengwa.update.UpdateState(app.pengwa.update.UpdateStage.Downloading, release, progress = 0.46f, activeSource = "GitHub 直连", downloadedBytes = 7_820_000, bytesPerSecond = 420_000)
        8 -> app.pengwa.update.UpdateState(app.pengwa.update.UpdateStage.Failed, release, error = "下载中断，已保留进度。请重试下载或切换线路", activeSource = "GitHub 直连")
        9 -> app.pengwa.update.UpdateState(app.pengwa.update.UpdateStage.Available, release, source = app.pengwa.update.UpdateSource.GitHub)
        else -> app.pengwa.update.UpdateState()
    }
}

/** Exercises the actual updater and saved line choice without onboarding or exchange hardware. */
@Composable
internal fun UpdateLivePreview() {
    PreviewSurface {
        SettingsPage(onBack = {}, modifier = Modifier.safeDrawingPadding()) {
            SettingsContent(
                onShowGuide = {},
                backgroundBroadcast = { SettingsRow(stringResource(R.string.bg_setting_title), stringResource(R.string.bg_setting_desc), false) {} },
                notification = { SettingsRow(stringResource(R.string.notif_setting_title), stringResource(R.string.notif_setting_desc), false) {} },
                update = { AppUpdateSection() },
            )
        }
    }
}
