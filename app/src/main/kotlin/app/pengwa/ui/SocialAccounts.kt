package app.pengwa.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import app.pengwa.R
import app.pengwa.identity.CardBlock
import app.pengwa.identity.SocialPlatform
import app.pengwa.identity.socialProfileUrl

private val SocialPlatform.icon: Int get() = when (this) {
    SocialPlatform.QQ -> R.drawable.social_qq
    SocialPlatform.WeChat -> R.drawable.social_wechat
    SocialPlatform.Bilibili -> R.drawable.social_bilibili
    SocialPlatform.Xiaohongshu -> R.drawable.social_xiaohongshu
    SocialPlatform.X -> R.drawable.social_x
    SocialPlatform.Telegram -> R.drawable.social_telegram
}

@Composable
private fun SocialIcon(platform: SocialPlatform?) {
    Icon(painterResource(platform?.icon ?: R.drawable.ph_users), contentDescription = null,
        modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun SocialAccountBlock(block: CardBlock, onOpen: (String) -> Unit) {
    val platform = SocialPlatform.fromTitle(block.title)
    val label = platform?.label ?: block.title.ifBlank { "社交账号" }
    val context = LocalContext.current
    var copied by remember(block.text, block.title) { mutableStateOf(false) }
    val profile = block.socialProfileUrl()
    Surface(
        onClick = {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(label, block.text))
            copied = true
        },
        enabled = block.text.isNotBlank(),
        modifier = Modifier.semantics {
            contentDescription = "$label，${block.text}，点击复制账号"
            stateDescription = if (copied) "已复制" else ""
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialIcon(platform)
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(block.text, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (copied) "已复制" else "点击复制", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (profile != null) IconButton(onClick = { onOpen(profile) }) {
                    Icon(painterResource(R.drawable.ph_arrow_right), contentDescription = "打开${label}主页")
                }
            }
        }
    }
}

@Composable
internal fun SocialAccountFields(block: CardBlock, onChange: (CardBlock) -> Unit) {
    var editingCustomTitle by remember { mutableStateOf(false) }
    val recognized = SocialPlatform.fromTitle(block.title)
    val platform = if (editingCustomTitle) null else recognized
    Text("社交平台", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SocialPlatform.entries.forEach { choice ->
            FilterChip(selected = platform == choice, onClick = { editingCustomTitle = false; onChange(block.copy(title = choice.label)) },
                label = { Text(choice.label) }, leadingIcon = { SocialIcon(choice) })
        }
        FilterChip(selected = platform == null, onClick = { if (recognized != null) onChange(block.copy(title = "")) }, label = { Text("其他") })
    }
    if (platform == null) OutlinedTextField(block.title, { onChange(block.copy(title = it)) },
        label = { Text("平台名称") }, modifier = Modifier.fillMaxWidth().onFocusChanged { editingCustomTitle = it.isFocused })
    OutlinedTextField(block.text, { onChange(block.copy(text = it)) },
        label = { Text(platform?.accountLabel ?: "账号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(block.url, { onChange(block.copy(url = it)) }, label = { Text("主页链接（可选）") },
        supportingText = { Text(when (platform) {
            SocialPlatform.QQ, SocialPlatform.WeChat -> "可复制账号后在平台内添加；也可填写分享链接"
            SocialPlatform.Xiaohongshu -> "如需打开主页，请粘贴平台分享的主页链接"
            SocialPlatform.Bilibili -> "填写数字 UID 可自动生成主页链接，也可粘贴分享链接"
            SocialPlatform.X, SocialPlatform.Telegram -> "填写用户名可自动生成主页链接，也可粘贴分享链接"
            null -> "粘贴平台分享的 http 或 https 主页链接"
        }) }, modifier = Modifier.fillMaxWidth())
}
