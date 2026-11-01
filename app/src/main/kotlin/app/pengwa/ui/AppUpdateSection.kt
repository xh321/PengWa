package app.pengwa.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pengwa.BuildConfig
import app.pengwa.update.*
import java.util.Locale

@Composable
internal fun AppUpdateSection(model: UpdateViewModel = viewModel()) {
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val install: () -> Unit = {
        try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            } else model.prepareInstall { file ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (e: Exception) { throw UpdateFailure("无法打开系统安装器，请检查系统设置后重试") }
            }
        } catch (e: Exception) { model.fail("无法打开安装界面，请检查系统是否允许安装应用后重试") }
    }
    AppUpdateContent(state, model::check, model::download, install, model::selectSource)
}

@Composable
internal fun AppUpdateContent(state: UpdateState, onCheck: () -> Unit, onDownload: () -> Unit, onInstall: () -> Unit, onSource: (UpdateSource) -> Unit = {}) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("应用更新", style = MaterialTheme.typography.titleMedium)
        Text("当前版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}） · ${if (BuildConfig.DEBUG) "Nightly" else "正式版"}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("更新线路", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UpdateSource.entries.forEach { source ->
                FilterChip(selected = state.source == source, onClick = { onSource(source) },
                    enabled = state.stage !in listOf(UpdateStage.Checking, UpdateStage.Downloading, UpdateStage.Ready),
                    label = { Text(source.label) })
            }
        }
        Text(if (state.source == UpdateSource.GitHub) "直接从 GitHub 检查并下载更新。"
            else "加速线路经第三方 ${java.net.URI(BuildConfig.UPDATE_PROXY_BASE_URL).host} 转发公开更新文件。${if (state.source == UpdateSource.Auto) "失败时自动切换 GitHub 直连。" else "可手动切换 GitHub 直连。"}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val status = when (state.stage) {
            UpdateStage.Idle -> "手动检查新版本，下载前会显示版本信息。"
            UpdateStage.Checking -> "正在检查更新…"
            UpdateStage.Current -> "当前已是此通道的最新版本"
            UpdateStage.Available -> "发现新版本 ${state.release?.versionName}"
            UpdateStage.Downloading -> "正在下载 ${(state.progress * 100).toInt()}%"
            UpdateStage.Ready -> "安装包已校验。首次安装需允许此应用安装更新，授权后请再次点击安装。"
            UpdateStage.Failed -> state.error.orEmpty()
        }
        Text(status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.stage == UpdateStage.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        state.activeSource?.let { Text("当前线路：$it", style = MaterialTheme.typography.bodySmall) }
        if (state.stage == UpdateStage.Downloading) {
            Text("${formatMegabytes(state.downloadedBytes)} / ${formatMegabytes(state.release?.size ?: 0)} MB" +
                if (state.bytesPerSecond > 0) " · ${formatMegabytes(state.bytesPerSecond)} MB/s" else " · 正在连接或续传…",
                style = MaterialTheme.typography.bodySmall)
        }
        if (state.stage == UpdateStage.Checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.stage == UpdateStage.Downloading) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        if (state.stage == UpdateStage.Available) state.release?.let { release ->
            Text("下载大小 ${String.format(Locale.ROOT, "%.1f", release.size / 1048576.0)} MB · 安装时需系统确认",
                style = MaterialTheme.typography.bodySmall)
            if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodyMedium)
        }
        when (state.stage) {
            UpdateStage.Available -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDownload) { Text("下载更新") }
                TextButton(onClick = onCheck) { Text("重新检查") }
            }
            UpdateStage.Failed -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.release != null) Button(onClick = onDownload) { Text("重试下载") }
                OutlinedButton(onClick = onCheck) { Text("重试检查") }
            }
            UpdateStage.Ready -> Button(onClick = onInstall) { Text("安装更新") }
            UpdateStage.Checking, UpdateStage.Downloading -> Unit
            else -> OutlinedButton(onClick = onCheck) { Text(if (state.stage == UpdateStage.Failed) "重试检查" else "检查更新") }
        }
    }
}

private fun formatMegabytes(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes / 1048576.0)
