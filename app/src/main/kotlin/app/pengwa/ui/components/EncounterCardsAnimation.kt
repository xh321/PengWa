package app.pengwa.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pengwa.ui.theme.LocalStatusColors
import kotlin.math.PI
import kotlin.math.sin

/** Shared vector motion for an encounter request and its completed state. */
@Composable
internal fun EncounterCardsAnimation(
    name: String,
    celebrating: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val motionEnabled = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    when {
        !motionEnabled -> EncounterCards(name, phase = 1f, celebrating = celebrating, modifier = modifier)
        celebrating -> {
            val progress = remember { Animatable(0f) }
            LaunchedEffect(Unit) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            EncounterCards(name, progress.value, celebrating = true, modifier = modifier)
        }
        else -> {
            val transition = rememberInfiniteTransition(label = "encounter-request")
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "encounter-cards",
            )
            EncounterCards(name, phase, celebrating = false, modifier = modifier)
        }
    }
}

@Composable
private fun EncounterCards(name: String, phase: Float, celebrating: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val statusColors = LocalStatusColors.current
    Box(modifier.fillMaxWidth().height(152.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val pulse = 0.85f + phase * 0.15f
            drawCircle(
                color = colors.primary.copy(alpha = if (celebrating) 0.10f else 0.06f + phase * 0.04f),
                radius = size.minDimension * 0.46f * pulse,
                center = center.copy(y = centerY),
            )
            drawCircle(
                color = colors.primary.copy(alpha = 0.08f),
                radius = size.minDimension * 0.34f,
                center = center.copy(y = centerY),
            )
            sparkles.forEach { sparkle ->
                val wave = (sin((phase + sparkle.delay) * 2 * PI).toFloat() + 1f) / 2f
                val alpha = if (celebrating) 0.25f + wave * 0.65f else 0.16f + wave * 0.24f
                val radius = sparkle.radius.dp.toPx() * (0.75f + wave * 0.35f)
                val position = center.copy(x = size.width * sparkle.x, y = size.height * sparkle.y)
                drawCircle(colors.primary.copy(alpha = alpha), radius = radius, center = position)
                if (celebrating) {
                    drawLine(
                        color = colors.primary.copy(alpha = alpha),
                        start = position.copy(x = position.x - radius * 2.2f),
                        end = position.copy(x = position.x + radius * 2.2f),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                    drawLine(
                        color = colors.primary.copy(alpha = alpha),
                        start = position.copy(y = position.y - radius * 2.2f),
                        end = position.copy(y = position.y + radius * 2.2f),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            EncounterCard(
                name = name,
                rotation = -12f + phase * 7f,
                scale = 0.94f + phase * 0.06f,
                highlighted = true,
            )
            Surface(
                shape = CircleShape,
                color = if (celebrating) statusColors.successContainer else colors.primary,
                shadowElevation = 4.dp,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    PengWaIcon(
                        if (celebrating) PengWaIcons.Success else PengWaIcons.Bluetooth,
                        tint = if (celebrating) statusColors.success else colors.onPrimary,
                        modifier = Modifier.graphicsLayer { scaleX = 0.9f + phase * 0.1f; scaleY = scaleX },
                    )
                }
            }
            EncounterCard(
                name = null,
                rotation = 12f - phase * 7f,
                scale = 0.94f + phase * 0.06f,
                highlighted = false,
            )
        }
    }
}

@Composable
private fun EncounterCard(name: String?, rotation: Float, scale: Float, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(width = 68.dp, height = 88.dp).graphicsLayer {
            rotationZ = rotation
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(20.dp),
        color = if (highlighted) colors.primaryContainer else colors.surface,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = if (highlighted) 0.35f else 0.16f)),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(shape = CircleShape, color = colors.primary.copy(alpha = 0.72f), modifier = Modifier.size(7.dp)) {}
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (name == null) {
                    PengWaIcon(PengWaIcons.IdentityFill, tint = colors.primary, modifier = Modifier.size(30.dp))
                } else {
                    Text(
                        name.trim().let { if (it.isEmpty()) "?" else it.substring(0, it.offsetByCodePoints(0, 1)) },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimaryContainer,
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = colors.primary.copy(alpha = 0.16f),
                modifier = Modifier.width(26.dp).height(4.dp),
            ) {}
        }
    }
}

private data class Sparkle(val x: Float, val y: Float, val radius: Float, val delay: Float)

private val sparkles = listOf(
    Sparkle(0.12f, 0.24f, 3.0f, 0.0f),
    Sparkle(0.24f, 0.78f, 2.2f, 0.3f),
    Sparkle(0.78f, 0.18f, 2.6f, 0.6f),
    Sparkle(0.90f, 0.66f, 3.2f, 0.2f),
    Sparkle(0.70f, 0.86f, 2.0f, 0.8f),
)
