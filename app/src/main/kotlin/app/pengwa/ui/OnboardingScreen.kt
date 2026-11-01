package app.pengwa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pengwa.identity.OnboardingStep
import app.pengwa.MainActivity
import app.pengwa.ui.components.PengWaIcon
import app.pengwa.ui.components.PengWaIcons

/** Card and progress are committed together; restarting resumes at the last saved step. */
@Composable
internal fun OnboardingScreen(
    viewModel: IdentityViewModel,
    onFinish: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var error by remember { mutableStateOf<String?>(null) }
    val step = viewModel.onboardingStep
    fun moveTo(next: OnboardingStep) { error = viewModel.moveOnboardingTo(next) }
    BackHandler(enabled = step == OnboardingStep.PreviewCard) { moveTo(OnboardingStep.CreateCard) }

    Column(modifier.fillMaxSize()) {
        OnboardingProgress(step)
        error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
        when (step) {
            OnboardingStep.CreateCard -> {
                LaunchedEffect(Unit) { viewModel.beginOnboardingEdit() }
                viewModel.draft?.let { draft ->
                    CardEditorContent(
                        draft = draft,
                        onChange = viewModel::updateDraft,
                        onSave = viewModel::saveOnboardingDraft,
                        onClose = {},
                        onboarding = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            OnboardingStep.PreviewCard -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("这就是别人收到的你", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text("确认一下卡片内容。交换时，对方会收到这张完整卡片，包括你填写的社媒和链接。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        viewModel.identity?.let { UserCardContent(it) }
                        if (viewModel.identity?.card?.blocks.isNullOrEmpty()) {
                            Text("简单的名字也能开启一次相遇。之后可在主页点铅笔，补充头像和内容。", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Button(onClick = { moveTo(OnboardingStep.LearnExchange) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("卡片准备好了，学习交换") }
                TextButton(onClick = { moveTo(OnboardingStep.CreateCard) }, modifier = Modifier.fillMaxWidth()) { Text("返回修改") }
            }
            OnboardingStep.LearnExchange -> ExchangeGuide(
                onBack = { moveTo(OnboardingStep.PreviewCard) },
                onFinish = { tab ->
                    error = viewModel.moveOnboardingTo(OnboardingStep.Complete)
                    if (error == null) onFinish(tab)
                },
                modifier = Modifier.weight(1f),
            )
            OnboardingStep.Complete -> Unit
        }
    }
}

@Composable
private fun OnboardingProgress(current: OnboardingStep) {
    val step = current.storedValue.coerceIn(0, 2)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("第 ${step + 1} / 3 步 · ${listOf("创建卡片", "预览确认", "学会交换")[step.coerceIn(0, 2)]}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
    }
}

private enum class ExchangeLesson(val label: String, val icon: Int) {
    Qr("扫码", PengWaIcons.Qr), Nearby("附近", PengWaIcons.Nearby), Nfc("碰一碰", PengWaIcons.Tap),
}

/** Read-only lessons: hardware permissions are requested by the actual exchange destination. */
@Composable
internal fun ExchangeGuide(onBack: () -> Unit, onFinish: (Int) -> Unit, modifier: Modifier = Modifier) {
    var lesson by rememberSaveable { mutableStateOf(ExchangeLesson.Qr) }
    BackHandler(onBack = onBack)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("见面了，交换一张卡片", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text("双方都需要安装碰碰娃、开启蓝牙，并按提示授权（旧版 Android 可能提示定位权限）。第一次见面，推荐扫码。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExchangeLesson.entries.forEach { item ->
                FilterChip(
                    selected = lesson == item,
                    onClick = { lesson = item },
                    label = { Text(item.label) },
                    leadingIcon = { PengWaIcon(item.icon, tint = if (lesson == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                when (lesson) {
                    ExchangeLesson.Qr -> {
                        LessonStep(1, "一个出示，一个扫码", "两人打开主页。一人出示自己的二维码，另一人点“扫一扫”对准它。")
                        LessonStep(2, "出示方确认申请", "扫码会发起蓝牙交换申请。出示二维码的人收到申请后，核对名字并点“同意”。")
                        LessonStep(3, "完成后去集邮册", "等卡片交换完成，对方就会出现在“集邮册”中，可打开查看完整卡片。")
                    }
                    ExchangeLesson.Nearby -> {
                        LessonStep(1, "两人进入附近娃娃", "打开底部“附近娃娃”，允许附近设备权限，等待列表里出现对方。")
                        LessonStep(2, "核对名字，申请交换", "找到对方后点“申请交换”，请对方在收到的申请中点“同意”。")
                        LessonStep(3, "等待卡片交换完成", "交换完成后去“集邮册”查看；如果没有找到对方，可以改用扫码。")
                    }
                    ExchangeLesson.Nfc -> {
                        LessonStep(1, "一个出示，一个读取", "两人打开主页，在碰一碰区域，一人选“出示”，另一人选“读取”。")
                        LessonStep(2, "手机 NFC 区域贴近", "双方开启 NFC 和蓝牙，解锁手机并停留在主页。将两部手机的 NFC 区域靠在一起。")
                        LessonStep(3, "等待交换完成", "双向识别成功后会通过蓝牙交换卡片。没有反应、手机不支持 NFC 或钱包拦截时，改用扫码。")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            PengWaIcon(PengWaIcons.Book, tint = MaterialTheme.colorScheme.primary)
            Text("认准“交换完成”再离开。只扫描或识别成功，还不算收到完整卡片；收到的卡片保存在本机集邮册。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { onFinish(if (lesson == ExchangeLesson.Nearby) MainActivity.TAB_NEARBY else MainActivity.TAB_ME) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(when (lesson) { ExchangeLesson.Qr -> "去主页，试试扫码"; ExchangeLesson.Nearby -> "去附近，找娃友"; ExchangeLesson.Nfc -> "去主页，试试碰一碰" })
        }
        Text("以后可在主页设置里重看“如何交换卡片”。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
    }
}

@Composable
private fun LessonStep(number: Int, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
            Text(number.toString(), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
