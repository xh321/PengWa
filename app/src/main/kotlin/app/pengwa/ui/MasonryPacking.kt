package app.pengwa.ui

internal data class MasonryItem(val span: Int, val height: Int)
internal data class MasonryPlacement(val column: Int, val span: Int, val top: Int, val height: Int) {
    val bottom: Int get() = top + height
}

/** Pack into the first fitting gap, including holes above an earlier spanning item. */
internal fun packMasonry(items: List<MasonryItem>, columns: Int, gap: Int): List<MasonryPlacement> {
    require(columns > 0 && gap >= 0)
    val placed = mutableListOf<MasonryPlacement>()
    for (item in items) {
        require(item.height >= 0)
        val span = item.span.coerceIn(1, columns)
        val tops = (listOf(0) + placed.map { it.bottom + gap }).distinct().sorted()
        val position = tops.firstNotNullOf { top ->
            (0..columns - span).firstOrNull { column ->
                placed.none { other ->
                    column < other.column + other.span && other.column < column + span &&
                        top < other.bottom + gap && other.top < top + item.height + gap
                }
            }?.let { column -> MasonryPlacement(column, span, top, item.height) }
        }
        placed += position
    }
    return placed
}
