package app.pengwa.ui

import kotlin.test.*

class MasonryPackingTest {
    @Test fun `a later small block fills the hole beside text above a full width link`() {
        val positions = packMasonry(listOf(MasonryItem(1, 120), MasonryItem(2, 88), MasonryItem(1, 96)), 2, 12)
        assertEquals(MasonryPlacement(0, 1, 0, 120), positions[0])
        assertEquals(MasonryPlacement(0, 2, 132, 88), positions[1])
        assertEquals(MasonryPlacement(1, 1, 0, 96), positions[2])
    }

    @Test fun `mixed spans never overlap and preserve gaps in every column`() {
        val items = listOf(120, 74, 100, 240, 88, 160, 64, 120, 80, 48, 180, 100)
            .mapIndexed { index, height -> MasonryItem(if (index % 3 == 1) 2 else 1, height) }
        for (columns in 1..3) {
            val positions = packMasonry(items, columns, 12)
            assertEquals(items.size, positions.size)
            positions.forEachIndexed { index, a ->
                assertTrue(a.column >= 0 && a.column + a.span <= columns)
                assertEquals(items[index].height, a.height)
                for (b in positions.take(index)) {
                    val separateColumns = a.column + a.span <= b.column || b.column + b.span <= a.column
                    assertTrue(separateColumns || a.bottom + 12 <= b.top || b.bottom + 12 <= a.top)
                }
            }
        }
    }

    @Test fun `one column clamps spans and empty cards have no placements`() {
        assertTrue(packMasonry(emptyList(), 2, 12).isEmpty())
        assertEquals(listOf(MasonryPlacement(0, 1, 0, 60), MasonryPlacement(0, 1, 72, 100)),
            packMasonry(listOf(MasonryItem(2, 60), MasonryItem(1, 100)), 1, 12))
    }
}
