package app.pengwa.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.pengwa.analytics.AnalyticsEvent
import app.pengwa.identity.*
import app.pengwa.pengWa
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pengwa.ui.components.NameStamp
import app.pengwa.ui.components.EncounterCardsAnimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.withContext

@Composable
internal fun CardAvatar(identity: Identity, modifier: Modifier = Modifier, imageSize: androidx.compose.ui.unit.Dp = 56.dp) {
    val painter = rememberCardImagePainter(identity.card.avatar)
    val state by painter.state.collectAsState()
    Box(modifier) {
        if (state !is AsyncImagePainter.State.Success) NameStamp(identity.displayName)
        else Image(painter, "头像", Modifier.size(imageSize).clip(CircleShape), contentScale = ContentScale.Crop)
    }
}

/** A shared page shell keeps navigation reachable while long cards scroll. */
@Composable
internal fun CardDetailsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge)
            if (onEdit != null) TextButton(onClick = onEdit) { Text("编辑") }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(app.pengwa.ui.theme.PageGutter),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun UserCardContent(identity: Identity, actions: @Composable () -> Unit = {}) {
    val uriHandler = LocalUriHandler.current
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardAvatar(identity)
            Text(identity.displayName, style = MaterialTheme.typography.titleLarge)
        }
        actions()
        CardMasonry(identity.card.blocks) {
            identity.card.blocks.forEach { block ->
                if (block.type == BlockType.Image) {
                    CardImage(block.image, block.title.ifBlank { "卡片图片" })
                } else if (block.type == BlockType.Social) {
                    SocialAccountBlock(block) { url ->
                        runCatching { uriHandler.openUri(url) }.onFailure { error = "无法打开链接，请检查是否安装浏览器" }
                    }
                } else Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (block.title.isNotBlank()) Text(block.title, style = MaterialTheme.typography.titleSmall)
                        if (block.type != BlockType.Link) Text(block.text, style = MaterialTheme.typography.bodyMedium)
                        val url = if (block.type == BlockType.Link) block.text else block.url
                        if (CardCodec.validLink(url)) TextButton(onClick = {
                            runCatching { uriHandler.openUri(url) }.onFailure { error = "无法打开链接，请检查是否安装浏览器" }
                        }) { Text(url) }
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** Scale to the available space without cropping, shaping, or painting behind transparent pixels. */
@Composable
private fun CardImage(value: String, description: String) {
    val painter = rememberCardImagePainter(value)
    val state by painter.state.collectAsState()
    val image = (state as? AsyncImagePainter.State.Success)?.result?.image
    if (image == null) {
        if (state is AsyncImagePainter.State.Error) Text("图片无法显示", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val ratio = image.width.toFloat() / image.height
        val width = minOf(maxWidth, image.width.dp, 240.dp * ratio)
        Image(painter, description, Modifier.width(width).aspectRatio(ratio), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun rememberCardImagePainter(value: String): AsyncImagePainter {
    val context = LocalContext.current
    val request = remember(context, value) {
        ImageRequest.Builder(context).data(CardImageSource(value)).size(Size.ORIGINAL).build()
    }
    return rememberAsyncImagePainter(request)
}

/** Draft stays in a ViewModel, including across rotation and the system image picker. */
@Composable
internal fun CardEditor(viewModel: IdentityViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(viewModel) { if (viewModel.draft == null) viewModel.beginEdit() }
    val app = LocalContext.current.pengWa
    val analytics = app.analytics
    val draft = viewModel.draft ?: return
    CardEditorContent(
        draft, viewModel::updateDraft, viewModel::saveDraft, onClose, modifier,
        onCancel = { analytics.track(AnalyticsEvent.CardEditCancelled) },
        online = app.onlineCards.hasLink(),
        onPreview = { analytics.track(AnalyticsEvent.CardEditorPreviewOpened) },
    )
}

@Composable
internal fun CardEditorContent(
    draft: Identity,
    onChange: (Identity) -> Unit,
    onSave: suspend () -> String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onboarding: Boolean = false,
    onCancel: () -> Unit = {},
    onPreview: () -> Unit = {},
    online: Boolean = false,
) {
    var preview by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var imageTarget by rememberSaveable { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val latestDraft by rememberUpdatedState(draft)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun close() { confirmDiscard = true }
    BackHandler(enabled = !onboarding || preview) { if (preview) preview = false else close() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = imageTarget
        if (uri != null && target != null) scope.launch {
            loading = true
            runCatching { withContext(Dispatchers.IO) { CardImages.import(context, uri, target == -1) } }
                .onSuccess { image ->
                    val current = latestDraft
                    if (target != -1 && target !in current.card.blocks.indices) {
                        error = "原来的图片块已不存在，请重新选择"
                    } else {
                        val card = if (target == -1) current.card.copy(avatar = image) else current.card.copy(blocks = current.card.blocks.mapIndexed { i, block -> if (i == target) block.copy(image = image) else block })
                        onChange(current.copy(card = card))
                    }
                }.onFailure { error = it.message ?: "图片读取失败" }
            loading = false
        }
    }
    fun changeBlock(index: Int, block: CardBlock) {
        onChange(draft.copy(card = draft.card.copy(blocks = draft.card.blocks.toMutableList().also { it[index] = block })))
        error = null
    }
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (preview) "卡片预览" else if (onboarding) "创建我的卡片" else "编辑我的卡片", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = {
                preview = !preview
                if (preview) onPreview()
            }, enabled = !loading) { Text(if (preview) "继续编辑" else "预览") }
        }
        if (onboarding && !preview) Text("先填一个让娃友认出你的名字。头像、自我介绍和社媒都可选，以后也能随时修改。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (online && !preview) Text(
            "保存后会同步到在线名片，持有链接的人可以看到更新。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preview) UserCardContent(draft) else {
            OutlinedTextField(draft.displayName, { onChange(draft.copy(displayName = it)); error = null }, label = { Text("显示名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardAvatar(draft)
                TextButton(onClick = { imageTarget = -1; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !loading) { Text("选择头像") }
                if (draft.card.avatar.isNotEmpty()) TextButton(onClick = { onChange(draft.copy(card = draft.card.copy(avatar = ""))) }) { Text("移除") }
            }
            Text("内容块 · ${draft.card.blocks.size}/${CardCodec.MAX_BLOCKS}", style = MaterialTheme.typography.titleMedium)
            draft.card.blocks.forEachIndexed { index, block ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1} · ${block.type.label()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (block.type == BlockType.Social) SocialAccountFields(block) { changeBlock(index, it) }
                        if (block.type != BlockType.Image && block.type != BlockType.Social) OutlinedTextField(block.title, { changeBlock(index, block.copy(title = it)) }, label = { Text("标题（可选）") }, modifier = Modifier.fillMaxWidth())
                        if (block.type != BlockType.Image && block.type != BlockType.Social) OutlinedTextField(block.text, { changeBlock(index, block.copy(text = it)) }, label = { Text(when (block.type) { BlockType.Link -> "https://…"; else -> "文字内容" }) }, modifier = Modifier.fillMaxWidth())
                        if (block.type == BlockType.Image) {
                            if (block.image.isNotEmpty()) CardImage(block.image, "已选择的图片")
                            TextButton(onClick = { imageTarget = index; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !loading) { Text("选择图片") }
                        }
                        Row {
                            TextButton(enabled = index > 0 && !loading, onClick = {
                                val blocks = draft.card.blocks.toMutableList(); java.util.Collections.swap(blocks, index, index - 1)
                                onChange(draft.copy(card = draft.card.copy(blocks = blocks)))
                            }) { Text("上移") }
                            TextButton(enabled = index < draft.card.blocks.lastIndex && !loading, onClick = {
                                val blocks = draft.card.blocks.toMutableList(); java.util.Collections.swap(blocks, index, index + 1)
                                onChange(draft.copy(card = draft.card.copy(blocks = blocks)))
                            }) { Text("下移") }
                            TextButton(enabled = !loading, onClick = { onChange(draft.copy(card = draft.card.copy(blocks = draft.card.blocks.filterIndexed { i, _ -> i != index }))) }) { Text("删除") }
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockType.entries.forEach { type ->
                    OutlinedButton(enabled = draft.card.blocks.size < CardCodec.MAX_BLOCKS && !loading, onClick = {
                        onChange(draft.copy(card = draft.card.copy(blocks = draft.card.blocks + CardBlock(type, title = if (type == BlockType.Social) SocialPlatform.QQ.label else ""))))
                    }) { Text("+ ${type.label()}") }
                }
            }
            Text("图片会压缩为小图。扫码和附近娃娃通过蓝牙交换，NFC 标签可打开在线名片。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !loading, onClick = { if (!loading) { loading = true; scope.launch {
            try { error = onSave(); if (error == null) onClose() } finally { loading = false }
        } } }, modifier = Modifier.fillMaxWidth()) { Text(if (onboarding) "保存并预览" else "保存卡片") }
        if (!onboarding) TextButton(onClick = ::close, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Text("取消") }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("放弃本次编辑？") }, text = { Text("尚未保存的修改将被丢弃。") }, confirmButton = { TextButton(onClick = { onCancel(); onClose() }) { Text("放弃") } }, dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } })
}

private fun BlockType.label(): String = when (this) { BlockType.Text -> "文字"; BlockType.Link -> "链接"; BlockType.Social -> "社媒"; BlockType.Image -> "小图" }

@Composable
internal fun HomeBluetoothStatus(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember { context.pengWa.bleExchange }
    var permitted by remember { mutableStateOf(app.pengwa.ble.BlePermissions.granted(context)) }
    val bluetoothOn by controller.bluetoothOn.collectAsStateWithLifecycle()
    val status by controller.discoveryStatus.collectAsStateWithLifecycle()
    var denied by remember { mutableStateOf(false) }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { controller.refresh() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = app.pengwa.ble.BlePermissions.granted(context)
        denied = !permitted
        controller.refresh()
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        permitted = app.pengwa.ble.BlePermissions.granted(context)
        controller.refresh()
        onPauseOrDispose { }
    }
    if (!permitted || !bluetoothOn || status != null) HomeBluetoothNotice(
        message = when { !permitted -> "交换卡片需要蓝牙授权"; !bluetoothOn -> "请开启蓝牙以交换卡片"; else -> status.orEmpty() },
        modifier = modifier,
    ) {
        if (permitted && bluetoothOn && status != null && !status.orEmpty().contains("正在") && status != "卡片交换完成") TextButton(onClick = controller::retryDiscovery) { Text("重试连接", maxLines = 1) }
        if (!permitted || !bluetoothOn) TextButton(onClick = {
            if (!permitted && denied) context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
            else if (!permitted) permissions.launch(app.pengwa.ble.BlePermissions.required())
            else enable.launch(android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }) { Text(if (!permitted) { if (denied) "前往设置" else "授权蓝牙" } else "开启蓝牙", maxLines = 1) }
    }
}

@Composable
internal fun HomeBluetoothNotice(message: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium,
    ) {
        Column {
            if (message.startsWith("正在")) {
                EncounterCardsAnimation(name = "对方")
            }
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp).heightIn(min = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                action()
            }
        }
    }
}
