package app.pengwa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pengwa.R

/** Non-modal success feedback: the current page remains usable while the celebration is visible. */
@Composable
internal fun MeetingCelebrationOverlay(name: String, visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(240)),
        exit = fadeOut(tween(180)),
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                shadowElevation = 12.dp,
            ) {
                Box {
                    Column(
                        Modifier.fillMaxWidth().background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                    MaterialTheme.colorScheme.surface,
                                ),
                            ),
                        ).padding(horizontal = 24.dp, vertical = 20.dp).verticalScroll(rememberScrollState()).semantics(
                            mergeDescendants = true,
                        ) { liveRegion = LiveRegionMode.Polite },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EncounterCardsAnimation(name = name, celebrating = true)
                        Text(
                            stringResource(R.string.meeting_success_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.meeting_snackbar, name),
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.meeting_success_hint),
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                        PengWaIcon(PengWaIcons.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
