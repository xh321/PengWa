package app.pengwa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pengwa.MainActivity
import app.pengwa.R
import app.pengwa.ui.components.PengWaIcon
import app.pengwa.ui.components.PengWaIcons
import app.pengwa.ui.theme.ContentMaxWidth

private data class Destination(val tab: Int, val icon: Int, val selectedIcon: Int, val label: Int)

private val destinations = listOf(
    Destination(MainActivity.TAB_NEARBY, PengWaIcons.Nearby, PengWaIcons.NearbyFill, R.string.tab_nearby),
    Destination(MainActivity.TAB_ME, PengWaIcons.Tap, PengWaIcons.Tap, R.string.tab_exchange),
    Destination(MainActivity.TAB_CONTACTS, PengWaIcons.Book, PengWaIcons.BookFill, R.string.tab_contacts),
)

/** Bottom tabs on phones; a scrollable rail leaves enough height for an intact QR code in short landscape windows. */
@Composable
internal fun PengWaScaffold(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    val size = LocalWindowInfo.current.containerSize
    val useRail = with(LocalDensity.current) { size.height.toDp() < 480.dp && size.width > size.height }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = snackbarHost,
        bottomBar = { if (showNavigation && !useRail) PengWaNavigation(selectedTab, onSelect) },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (showNavigation && useRail) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                    containerColor = MaterialTheme.colorScheme.surface,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    destinations.forEach { item ->
                        if (item.tab == MainActivity.TAB_ME) {
                            PrimaryDestination(selectedTab == item.tab, { onSelect(item.tab) }, Modifier.width(80.dp))
                        } else NavigationRailItem(
                            selected = selectedTab == item.tab,
                            onClick = { onSelect(item.tab) },
                            icon = { DestinationIcon(item, selectedTab == item.tab) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                content(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize())
            }
        }
    }
}

@Composable
internal fun PengWaNavigation(selectedTab: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        destinations.forEach { item ->
            if (item.tab == MainActivity.TAB_ME) {
                PrimaryDestination(selectedTab == item.tab, { onSelect(item.tab) }, Modifier.weight(1f))
            } else NavigationBarItem(
                selected = selectedTab == item.tab,
                onClick = { onSelect(item.tab) },
                icon = { DestinationIcon(item, selectedTab == item.tab) },
                label = { Text(stringResource(item.label)) },
            )
        }
    }
}

@Composable
private fun DestinationIcon(item: Destination, selected: Boolean) {
    PengWaIcon(
        if (selected) item.selectedIcon else item.icon,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The core exchange destination stays prominent even while browsing the supporting tabs. */
@Composable
private fun PrimaryDestination(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 80.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = if (selected) 4.dp else 1.dp,
        ) {
            Box(Modifier.size(width = 72.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                PengWaIcon(PengWaIcons.Tap, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text(
                stringResource(R.string.tab_exchange),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
