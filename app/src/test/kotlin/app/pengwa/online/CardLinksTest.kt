package app.pengwa.online

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardLinksTest {
    @Test
    fun readsOnlyCanonicalLinksAtTheConfiguredOrigin() {
        val links = CardLinks("https://cards.example.org")
        val id = UUID.fromString("ea05e2d2-cc60-4f8b-81c8-74b158eb2143")
        val link = links.page(id)
        assertEquals(id, links.idFrom(link))
        assertNull(links.idFrom("$link?redirect=elsewhere"))
        assertNull(links.idFrom("$link/other"))
        assertNull(links.idFrom("https://cards.example.org.evil.test/c/$id"))
        assertNull(links.idFrom("http://cards.example.org/c/$id"))
        assertNull(links.idFrom("https://cards.example.org/c/1-1-1-1-1"))
        assertNull(CardLinks("").idFrom(link))
    }
}
