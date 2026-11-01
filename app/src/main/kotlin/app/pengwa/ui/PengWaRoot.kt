package app.pengwa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pengwa.MainActivity
import app.pengwa.BuildConfig
import app.pengwa.R
import app.pengwa.exchange.ExchangeMode
import app.pengwa.identity.OnboardingStep
import app.pengwa.pengWa
import app.pengwa.ui.components.ExchangeRequestDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 底部导航只保留三个目的地；实验室仅 Debug 版通过主页状态图标连点打开。 */
@Composable
fun PengWaRoot(
    requestedTab: Int?,
    onTabRequestHandled: () -> Unit,
    identityViewModel: IdentityViewModel = viewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(MainActivity.TAB_ME) }
    var showingDetail by remember { mutableStateOf(false) }
    var nfcRole by rememberSaveable { mutableStateOf(ExchangeMode.NfcDisplay) }

    // 通知栏按钮要求切换到某一页
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            selectedTab = when (requestedTab) {
                MainActivity.TAB_ME, MainActivity.TAB_NEARBY, MainActivity.TAB_CONTACTS -> requestedTab
                MainActivity.TAB_LAB -> if (BuildConfig.DEBUG) MainActivity.TAB_LAB else MainActivity.TAB_ME
                else -> MainActivity.TAB_ME
            }
            onTabRequestHandled()
        }
    }

    // 碰到人了（NFC、蓝牙或二维码）：震一下并提示。新事件会顶掉还没消失的旧提示。
    LaunchedEffect(resources) {
        context.pengWa.meetings.events.collectLatest { event ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(resources.getString(R.string.meeting_snackbar, event.name))
        }
    }

    // 有人通过蓝牙向我申请交换：必须我同意才会交换。应用在前台时弹对话框，在后台时是带按钮的通知
    val controller = remember { context.pengWa.bleExchange }
    val incoming by controller.incoming.collectAsStateWithLifecycle()
    incoming.firstOrNull()?.let { request ->
        ExchangeRequestDialog(
            name = request.peer.displayName, shortId = request.peer.id.toString().take(8),
            onAccept = { controller.respond(request.key, accept = true) },
            onDecline = { controller.respond(request.key, accept = false) },
        )
    }

    // 首次创建和教学完成后才展示导航；已有用户直接进入主页。
    val readyForExchange = identityViewModel.identity != null && identityViewModel.onboardingStep == OnboardingStep.Complete
    BackHandler(enabled = selectedTab == MainActivity.TAB_LAB) { selectedTab = MainActivity.TAB_ME }

    PengWaScaffold(
        selectedTab = selectedTab,
        showNavigation = readyForExchange && !showingDetail,
        onSelect = { selectedTab = it },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentModifier ->
        when {
            !readyForExchange -> OnboardingScreen(
                viewModel = identityViewModel,
                onFinish = { selectedTab = it },
                modifier = contentModifier,
            )
            selectedTab == MainActivity.TAB_ME -> HomeScreen(
                viewModel = identityViewModel,
                onDetailVisibilityChanged = { showingDetail = it },
                role = nfcRole,
                onRoleChange = { nfcRole = it },
                onMessage = showMessage,
                onOpenNearby = { selectedTab = MainActivity.TAB_NEARBY },
                onOpenLab = if (BuildConfig.DEBUG) ({ selectedTab = MainActivity.TAB_LAB }) else null,
                modifier = contentModifier,
            )
            selectedTab == MainActivity.TAB_NEARBY -> NearbyScreen(modifier = contentModifier)
            selectedTab == MainActivity.TAB_CONTACTS -> ContactsScreen(
                onMessage = showMessage, modifier = contentModifier,
                onDetailVisibilityChanged = { showingDetail = it },
                onExchange = { selectedTab = MainActivity.TAB_ME },
            )
            selectedTab == MainActivity.TAB_LAB && BuildConfig.DEBUG -> LabScreen(
                modifier = contentModifier,
                onClose = { selectedTab = MainActivity.TAB_ME },
            )
        }
    }
}
