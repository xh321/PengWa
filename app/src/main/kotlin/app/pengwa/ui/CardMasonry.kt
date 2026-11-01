package app.pengwa.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import app.pengwa.identity.BlockType
import app.pengwa.identity.CardBlock

/** At most twelve blocks, measured once and packed into the earliest fitting gaps.
 * Own no scroll container so cards also work inside dialogs and the editor's scrolling preview.
 */
@Composable
internal fun CardMasonry(blocks: List<CardBlock>, content: @Composable () -> Unit) {
    val textMeasurer = rememberTextMeasurer()
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val titleStyle = MaterialTheme.typography.titleSmall
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val minColumnWidth = (128.dp.toPx() * fontScale.coerceAtLeast(1f)).toInt()
        val columns = ((constraints.maxWidth + gap) / (minColumnWidth + gap)).coerceIn(1, 3)
        val columnWidth = ((constraints.maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(1)
        val textConstraints = Constraints(maxWidth = (columnWidth - 32.dp.roundToPx()).coerceAtLeast(1))
        val spans = measurables.indices.map { index ->
            val block = blocks.getOrNull(index)
            val preferred = when (block?.type) {
                BlockType.Link -> 2
                BlockType.Text -> {
                    // A paragraph needs room to read; short notes can stay compact.
                    val bodyLines = textMeasurer.measure(block.text, style = bodyStyle, constraints = textConstraints).lineCount
                    val titleLines = textMeasurer.measure(block.title, style = titleStyle, constraints = textConstraints).lineCount
                    if (bodyLines >= 3 || titleLines >= 2) 2 else 1
                }
                else -> 1
            }
            preferred.coerceAtMost(columns)
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints.fixedWidth(columnWidth * spans[index] + gap * (spans[index] - 1)))
        }
        val placements = packMasonry(placeables.mapIndexed { index, item -> MasonryItem(spans[index], item.height) }, columns, gap)
        val height = placements.maxOfOrNull { it.bottom } ?: 0
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            placements.forEachIndexed { index, position ->
                placeables[index].placeRelative(position.column * (columnWidth + gap), position.top)
            }
        }
    }
}
