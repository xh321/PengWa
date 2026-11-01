package app.pengwa.update

import java.net.URI
import kotlin.test.*
import org.junit.Test

class UpdateSourcesTest {
    private val sources = UpdateSources("https://gh-proxy.com/")
    private val original = "https://github.com/xh321/PengWa/releases/download/nightly/update.json"

    @Test fun autoIsTheDefaultAndPrefersAccelerationWithDirectFallback() {
        assertEquals(UpdateSource.Auto, UpdateState().source)
        val endpoints = sources.endpoints(original, UpdateSource.Auto)
        assertEquals("https://gh-proxy.com/$original", endpoints[0].url)
        assertEquals(original, endpoints[1].url)
    }

    @Test fun directChoiceNeverContactsProxy() {
        assertEquals(listOf(original), sources.endpoints(original, UpdateSource.GitHub).map { it.url })
        assertEquals(1, sources.endpoints(original, UpdateSource.Domestic).size)
    }

    @Test fun rejectsInsecureOrAmbiguousProxyConfiguration() {
        listOf("http://proxy.test/", "https://user@proxy.test/", "https://proxy.test/path/",
            "https://proxy.test/?key=value", "https://proxy.test/#fragment", "https://proxy.test:443/").forEach {
            assertFailsWith<IllegalArgumentException>(it) { UpdateSources(it) }
        }
    }

    @Test fun sourceCannotProxyOtherReposOrPrivateQueryStrings() {
        listOf("http://github.com/xh321/PengWa/releases/download/v1/app.apk",
            "https://github.com/another/repo/releases/download/v1/app.apk", "$original?token=secret", "$original#hash").forEach {
            assertFailsWith<IllegalArgumentException>(it) { sources.endpoints(it, UpdateSource.Auto) }
        }
    }

    @Test fun redirectsStayOnExplicitHttpsHosts() {
        assertTrue(sources.allowsConnection(URI("https://release-assets.githubusercontent.com/download")))
        assertTrue(sources.allowsConnection(URI("https://gh-proxy.com/download")))
        listOf("http://gh-proxy.com/a", "https://gh-proxy.com.evil.test/a", "https://user@gh-proxy.com/a",
            "https://gh-proxy.com:444/a", "https://arbitrary.test/a").forEach {
            assertFalse(sources.allowsConnection(URI(it)), it)
        }
    }
}
