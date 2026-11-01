package app.pengwa.update

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.Test

class ResumableUpdateDownloadTest {
    private val payload = "a synthetic signed-apk-sized payload".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
    private val release = UpdateRelease("app.pengwa.debug", 3, "test", 29,
        "https://github.com/xh321/PengWa/releases/download/test/test.apk", payload.size.toLong(), hash, "")
    private val direct = UpdateEndpoint("https://github.com/xh321/PengWa/releases/download/test/test.apk", "GitHub 直连")
    private val proxy = UpdateEndpoint("https://gh-proxy.com/${direct.url}", "国内加速")

    private fun partial(directory: File) = File(directory, "$hash.part.apk")
    private fun inDirectory(action: suspend (File) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("pengwa-update-test").toFile()
        try { action(directory) } finally { directory.deleteRecursively() }
    }

    @Test fun resumesAnExistingPartialWithVerifiedContentRange() = inDirectory { directory ->
        partial(directory).writeBytes(payload.copyOfRange(0, 7))
        val downloader = ResumableUpdateDownload { _, offset ->
            assertEquals(7L, offset)
            response(206, payload.copyOfRange(7, payload.size), "bytes 7-${payload.lastIndex}/${payload.size}")
        }
        assertContentEquals(payload, downloader.download(release, listOf(direct), directory) {}.readBytes())
    }

    @Test fun fullResponseToRangeRequestRestartsInsteadOfAppending() = inDirectory { directory ->
        partial(directory).writeBytes(payload.copyOfRange(0, 7))
        val downloader = ResumableUpdateDownload { _, _ -> response(200, payload) }
        assertContentEquals(payload, downloader.download(release, listOf(direct), directory) {}.readBytes())
    }

    @Test fun automaticFallbackDoesNotRetryAnUnreachableProxyBeforeDirect() = inDirectory { directory ->
        val visited = mutableListOf<String>()
        val downloader = ResumableUpdateDownload { url, _ ->
            visited += url.host
            if (url.host == "gh-proxy.com") throw IOException("offline")
            response(200, payload)
        }
        assertContentEquals(payload, downloader.download(release, listOf(proxy, direct), directory) {}.readBytes())
        assertEquals(listOf("gh-proxy.com", "github.com"), visited)
    }

    @Test fun interruptedDownloadKeepsBytesForNextRetryEvenAfterRecreatingDownloader() = inDirectory { directory ->
        val broken = ResumableUpdateDownload { _, _ -> response(200, payload, brokenAfter = 7) }
        assertFailsWith<UpdateFailure> { broken.download(release, listOf(direct), directory) {} }
        assertEquals(7L, partial(directory).length())
        val resumed = ResumableUpdateDownload { _, offset ->
            assertEquals(7L, offset)
            response(206, payload.copyOfRange(7, payload.size), "bytes 7-${payload.lastIndex}/${payload.size}")
        }
        assertContentEquals(payload, resumed.download(release, listOf(direct), directory) {}.readBytes())
    }

    @Test fun rangeNotSatisfiableRestartsFromZero() = inDirectory { directory ->
        partial(directory).writeBytes(payload.copyOfRange(0, 7))
        val offsets = mutableListOf<Long>()
        val downloader = ResumableUpdateDownload { _, offset ->
            offsets += offset
            if (offset > 0) response(416, byteArrayOf()) else response(200, payload)
        }
        assertContentEquals(payload, downloader.download(release, listOf(direct), directory) {}.readBytes())
        assertEquals(listOf(7L, 0L), offsets)
    }

    @Test fun wrongRangeCannotContaminateTheCompletedFile() = inDirectory { directory ->
        partial(directory).writeBytes(payload.copyOfRange(0, 7))
        var calls = 0
        val downloader = ResumableUpdateDownload { _, _ ->
            if (calls++ == 0) response(206, payload, "bytes 0-${payload.lastIndex}/${payload.size}")
            else response(200, payload)
        }
        assertContentEquals(payload, downloader.download(release, listOf(direct), directory) {}.readBytes())
        assertEquals(2, calls)
    }

    @Test fun hashMismatchDiscardsPoisonedMirrorBytesBeforeFallback() = inDirectory { directory ->
        val downloader = ResumableUpdateDownload { url, _ ->
            response(200, if (url.host == "gh-proxy.com") ByteArray(payload.size) else payload)
        }
        assertContentEquals(payload, downloader.download(release, listOf(proxy, direct), directory) {}.readBytes())
    }

    @Test fun oversizedResponseIsRejectedAndNotKeptForRetry() = inDirectory { directory ->
        val downloader = ResumableUpdateDownload { _, _ -> response(200, payload + payload) }
        assertFailsWith<UpdateFailure> { downloader.download(release, listOf(direct), directory) {} }
        assertFalse(partial(directory).exists())
    }

    @Test fun changedReleaseDeletesThePreviousPartial() = inDirectory { directory ->
        val stale = File(directory, "old-hash.part.apk").apply { writeBytes(byteArrayOf(1, 2)) }
        val downloader = ResumableUpdateDownload { _, offset -> assertEquals(0, offset.toInt()); response(200, payload) }
        downloader.download(release, listOf(direct), directory) {}
        assertFalse(stale.exists())
    }

    @Test fun completedCacheIsRehashedBeforeUse() = inDirectory { directory ->
        partial(directory).writeBytes(payload)
        val downloader = ResumableUpdateDownload { _, _ -> error("No request should be needed") }
        assertContentEquals(payload, downloader.download(release, listOf(direct), directory) {}.readBytes())
    }

    @Test fun cancellationIsPropagatedWithoutSwitchingSourcesOrDeletingPartial() = inDirectory { directory ->
        partial(directory).writeBytes(payload.copyOfRange(0, 7))
        var calls = 0
        val downloader = ResumableUpdateDownload { _, _ -> calls++; throw CancellationException("cancel") }
        assertFailsWith<CancellationException> { downloader.download(release, listOf(proxy, direct), directory) {} }
        assertEquals(1, calls)
        assertEquals(7, partial(directory).length().toInt())
    }

    private fun response(status: Int, bytes: ByteArray, range: String? = null, brokenAfter: Int? = null): HttpURLConnection =
        object : HttpURLConnection(URL(direct.url)) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
            override fun getResponseCode() = status
            override fun getHeaderField(name: String?) = if (name == "Content-Range") range else null
            override fun getInputStream(): InputStream {
                if (brokenAfter == null) return ByteArrayInputStream(bytes)
                return object : InputStream() {
                    private var readBytes = 0
                    override fun read(): Int = throw IOException("disconnected")
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (readBytes >= brokenAfter) throw IOException("disconnected")
                        val count = minOf(length, brokenAfter - readBytes)
                        bytes.copyInto(buffer, offset, readBytes, readBytes + count)
                        readBytes += count
                        return count
                    }
                }
            }
        }
}
