package app.pengwa.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.pengwa.identity.Identity
import app.pengwa.nfc.CardTagException
import app.pengwa.nfc.CardTagSession
import app.pengwa.pengWa
import kotlinx.coroutines.CancellationException

private enum class TagPhase { Confirm, Publish, Tap, Fetch, Done, RevokeConfirm, Revoke, Revoked }

@Composable
internal fun CardTagDialog(
    identity: Identity,
    writing: Boolean,
    nfcReady: Boolean,
    onDismiss: () -> Unit,
    onCard: (Identity) -> Unit,
) {
    val context = LocalContext.current
    val cards = context.pengWa.onlineCards
    val activity = context.findActivity() ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var phase by remember { mutableStateOf(if (writing) TagPhase.Confirm else TagPhase.Tap) }
    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val configured = cards.links.configured
    val busy = error == null && phase in setOf(TagPhase.Publish, TagPhase.Fetch, TagPhase.Revoke)

    LaunchedEffect(phase, attempt) {
        if (error != null) return@LaunchedEffect
        try {
            when (phase) {
                TagPhase.Publish -> {
                    link = cards.publish(identity)
                    phase = TagPhase.Tap
                }
                TagPhase.Tap -> lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    if (error != null) return@repeatOnLifecycle
                    val session = CardTagSession(activity)
                    context.pengWa.nfcDiagnostics.record("Card tag session started write=$writing")
                    if (writing) {
                        session.write(link)
                        context.pengWa.nfcDiagnostics.record("Card tag written")
                        phase = TagPhase.Done
                    } else {
                        val value = session.read()
                        if (value == null || cards.links.idFrom(value) == null) {
                            error = "这个标签里没有碰碰娃名片链接。"
                        } else {
                            link = value
                            context.pengWa.nfcDiagnostics.record("Card link read")
                            phase = TagPhase.Fetch
                        }
                    }
                }
                TagPhase.Fetch -> {
                    val peer = cards.read(checkNotNull(cards.links.idFrom(link)))
                    if (peer == null) {
                        error = "这张名片不存在或已停用。"
                        phase = TagPhase.Tap
                    } else {
                        onCard(peer)
                        onDismiss()
                    }
                }
                TagPhase.Revoke -> {
                    cards.revoke()
                    phase = TagPhase.Revoked
                }
                else -> Unit
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            context.pengWa.nfcDiagnostics.record("Card tag action failed phase=$phase type=${failure.javaClass.simpleName}")
            error = when (failure) {
                CardTagException.ReadOnly -> "这个标签是只读的，请换一枚可写标签。"
                CardTagException.TooSmall -> "标签容量不足，请换一枚容量更大的标签。"
                CardTagException.Unsupported -> "无法使用这个标签，请换一枚支持 NDEF 的标签。"
                CardTagException.Disabled -> "NFC 已关闭，请开启后重试。"
                else -> when (phase) {
                    TagPhase.Tap -> "标签连接中断，请将手机贴稳后重试。"
                    TagPhase.Fetch -> "无法加载名片，请检查网络后重试。"
                    TagPhase.Revoke -> "停用失败，请检查网络后重试。"
                    else -> "发布失败，请检查网络后重试。"
                }
            }
        }
    }

    val title = when (phase) {
        TagPhase.RevokeConfirm, TagPhase.Revoke -> "停用名片链接"
        TagPhase.Revoked -> "链接已停用"
        else -> if (writing) "写入 NFC" else "读取 NFC"
    }
    val description = error ?: when (phase) {
        TagPhase.Confirm -> when {
            !configured -> "当前版本尚未配置在线名片服务。"
            !nfcReady -> "设备支持并开启 NFC 后可以写入标签。"
            else -> "将「${identity.displayName}」的头像和卡片内容发布到网上。拿到链接的人可以看到名片及后续更新。标签上只会写入链接。"
        }
        TagPhase.Publish -> "正在发布名片…"
        TagPhase.Tap -> if (writing) "请将手机背面贴近可写 NFC 标签，写入时保持贴紧。标签原有内容会被覆盖。"
            else "请将手机背面贴近名片上的 NFC 标签。"
        TagPhase.Fetch -> "正在联网加载名片…"
        TagPhase.Done -> "标签已写入。对方碰标签即可打开「${identity.displayName}」的在线名片。"
        TagPhase.RevokeConfirm -> "停用后，已写入标签和已分享的链接都会失效。"
        TagPhase.Revoke -> "正在停用链接…"
        TagPhase.Revoked -> "原链接已失效。再次写入 NFC 会创建新的链接。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = {
            when {
                error != null -> TextButton(onClick = { error = null; attempt++ }) { Text("重试") }
                phase == TagPhase.Confirm && configured && nfcReady ->
                    TextButton(onClick = { phase = TagPhase.Publish }) { Text("发布并写入") }
                phase == TagPhase.RevokeConfirm ->
                    TextButton(onClick = { phase = TagPhase.Revoke }) { Text("停用链接") }
                phase == TagPhase.Done || phase == TagPhase.Revoked ->
                    TextButton(onClick = onDismiss) { Text("完成") }
            }
        },
        dismissButton = {
            if (phase != TagPhase.Done && phase != TagPhase.Revoked) {
                if (phase == TagPhase.Confirm && cards.hasLink()) {
                    TextButton(onClick = { phase = TagPhase.RevokeConfirm }) { Text("停用链接…") }
                }
                TextButton(onClick = onDismiss) { Text(if (busy) "关闭" else "取消") }
            }
        },
    )
}
