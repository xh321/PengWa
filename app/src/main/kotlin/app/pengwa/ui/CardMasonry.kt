package app.pengwa.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

/** At most twelve blocks: measure once, then place each next block in the shortest column.
 * Own no scroll container so cards also work inside dialogs and the editor's scrolling preview.
 */
@Composable
internal fun CardMasonry(content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val minColumnWidth = (128.dp.toPx() * fontScale.coerceAtLeast(1f)).toInt()
        val columns = ((constraints.maxWidth + gap) / (minColumnWidth + gap)).coerceIn(1, minOf(3, measurables.size.coerceAtLeast(1)))
        val columnWidth = ((constraints.maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(1)
        val heights = IntArray(columns)
        val placements = measurables.map { measurable ->
            val column = heights.indices.minBy { heights[it] }
            val y = heights[column]
            val placeable = measurable.measure(Constraints.fixedWidth(columnWidth))
            heights[column] += placeable.height + gap
            Triple(placeable, column * (columnWidth + gap), y)
        }
        val height = ((heights.maxOrNull() ?: 0) - gap).coerceAtLeast(0)
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            placements.forEach { (placeable, x, y) -> placeable.placeRelative(x, y) }
        }
    }
}
