package app.pengwa.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import app.pengwa.identity.BlockType

/** At most twelve blocks: measure once, then place each next block in the lowest available column group.
 * Own no scroll container so cards also work inside dialogs and the editor's scrolling preview.
 */
@Composable
internal fun CardMasonry(types: List<BlockType>, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val minColumnWidth = (128.dp.toPx() * fontScale.coerceAtLeast(1f)).toInt()
        val columns = ((constraints.maxWidth + gap) / (minColumnWidth + gap)).coerceIn(1, 3)
        val columnWidth = ((constraints.maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(1)
        val heights = IntArray(columns)
        val placements = measurables.mapIndexed { index, measurable ->
            // Links get a horizontal two-cell footprint; other content keeps one cell.
            // Keep the grid independent of item count so even a lone social block stays small.
            val span = (if (types.getOrNull(index) == BlockType.Link) 2 else 1).coerceAtMost(columns)
            val column = (0..columns - span).minBy { start ->
                (start until start + span).maxOf { heights[it] }
            }
            val y = (column until column + span).maxOf { heights[it] }
            val placeable = measurable.measure(Constraints.fixedWidth(columnWidth * span + gap * (span - 1)))
            for (covered in column until column + span) heights[covered] = y + placeable.height + gap
            Triple(placeable, column * (columnWidth + gap), y)
        }
        val height = ((heights.maxOrNull() ?: 0) - gap).coerceAtLeast(0)
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            placements.forEach { (placeable, x, y) -> placeable.placeRelative(x, y) }
        }
    }
}
