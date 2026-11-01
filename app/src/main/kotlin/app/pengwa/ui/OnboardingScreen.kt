package app.pengwa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pengwa.identity.OnboardingStep
import app.pengwa.MainActivity
import app.pengwa.R
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
                Button(onClick = { moveTo(OnboardingStep.LearnExchange) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("卡片准备好了，去扩列") }
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
        Text("第 ${step + 1} / 3 步 · ${listOf("创建卡片", "预览确认", "扩列")[step.coerceIn(0, 2)]}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
    }
}

private enum class ExchangeLesson(val label: String, val icon: Int) {
    Qr("扫码", PengWaIcons.Qr), Nearby("附近", PengWaIcons.Nearby), Nfc("碰一碰", PengWaIcons.Tap),
}

private data class IllustratedLesson(
    val image: Int,
    val imageDescription: String,
    val steps: List<String>,
)

private fun ExchangeLesson.illustration(): IllustratedLesson = when (this) {
    ExchangeLesson.Qr -> IllustratedLesson(
        R.drawable.onboarding_qr,
        "左侧小人出示手机二维码，右侧小人举起手机扫码；出示方确认后，两张卡片互换。",
        listOf("一人出示二维码，另一人扫一扫", "出示方核对名字，点“同意”", "等“交换完成”，到集邮册查看"),
    )
    ExchangeLesson.Nearby -> IllustratedLesson(
        R.drawable.onboarding_nearby,
        "两位娃友通过蓝牙发现彼此，在附近列表选中对方发起申请，收到申请的人确认后互换卡片。",
        listOf("两人打开“附近娃娃”", "找到对方，点“申请交换”", "对方点“同意”，完成后入册"),
    )
    ExchangeLesson.Nfc -> IllustratedLesson(
        R.drawable.onboarding_nfc,
        "两位娃友将手机背面的 NFC 感应区域贴近，出现确认标记后互换卡片。",
        listOf("主页一人选“出示”，一人选“读取”", "开启 NFC，手机背面贴近", "等“交换完成”，到集邮册查看"),
    )
}

/** Illustrations lead the lesson; short native captions stay scalable and accessible. */
@Composable
internal fun ExchangeGuide(onBack: () -> Unit, onFinish: (Int) -> Unit, modifier: Modifier = Modifier) {
    var lesson by rememberSaveable { mutableStateOf(ExchangeLesson.Qr) }
    val illustration = lesson.illustration()
    BackHandler(onBack = onBack)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("一起扩列吧", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text("两人打开碰碰娃，开启蓝牙并按提示授权。第一次扩列，推荐扫码。", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Surface(shape = MaterialTheme.shapes.large) {
            Image(
                painter = painterResource(illustration.image),
                contentDescription = illustration.imageDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            illustration.steps.forEachIndexed { index, caption ->
                LessonCaption(index + 1, caption)
            }
        }
        if (lesson == ExchangeLesson.Nfc) Text(
            "碰一碰没反应，或手机不支持 NFC？换扫码试试。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onFinish(if (lesson == ExchangeLesson.Nearby) MainActivity.TAB_NEARBY else MainActivity.TAB_ME) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(when (lesson) { ExchangeLesson.Qr -> "去主页，试试扫码"; ExchangeLesson.Nearby -> "去附近，找娃友"; ExchangeLesson.Nfc -> "去主页，试试碰一碰" })
        }
        Text("在主页设置里，随时重看“扩列图解”。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
    }
}

@Composable
private fun LessonCaption(number: Int, caption: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
            Text(number.toString(), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(caption, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
