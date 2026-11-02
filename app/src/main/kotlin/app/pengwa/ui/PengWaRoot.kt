package app.pengwa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pengwa.MainActivity
import app.pengwa.BuildConfig
import app.pengwa.identity.Identity
import app.pengwa.identity.OnboardingStep
import app.pengwa.pengWa
import app.pengwa.ui.components.ExchangeRequestDialog
import app.pengwa.ui.components.PeerCardDialog
import kotlinx.coroutines.delay
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
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(MainActivity.TAB_ME) }
    var showingDetail by remember { mutableStateOf(false) }
    var meetingPeer by remember { mutableStateOf<Identity?>(null) }
    var meetingSequence by remember { mutableIntStateOf(0) }

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

    // 对方卡片就是唯一的成功反馈：自动出现、自动消失，不增加必要操作。
    LaunchedEffect(Unit) {
        context.pengWa.meetings.events.collectLatest { event ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            meetingSequence += 1
            meetingPeer = event.peer
        }
    }
    LaunchedEffect(meetingSequence, meetingPeer) {
        if (meetingPeer != null) {
            delay(3_600)
            meetingPeer = null
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
    LaunchedEffect(selectedTab, readyForExchange) {
        if (readyForExchange) {
            val event = when (selectedTab) {
                MainActivity.TAB_ME -> app.pengwa.analytics.AnalyticsEvent.HomeViewed
                MainActivity.TAB_NEARBY -> app.pengwa.analytics.AnalyticsEvent.NearbyViewed
                MainActivity.TAB_CONTACTS -> app.pengwa.analytics.AnalyticsEvent.ContactsViewed
                else -> null
            }
            event?.let(context.pengWa.analytics::track)
        }
    }
    BackHandler(enabled = selectedTab == MainActivity.TAB_LAB) { selectedTab = MainActivity.TAB_ME }

    Box(Modifier.fillMaxSize()) {
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
        meetingPeer?.let { peer ->
            key(meetingSequence) {
                PeerCardDialog(
                    peer = peer,
                    onDismiss = { meetingPeer = null },
                )
            }
        }
    }
}
