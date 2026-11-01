package app.pengwa.update

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class UpdateHttpClient(private val sources: UpdateSources) {
    suspend fun connect(initial: URL, offset: Long = 0): HttpURLConnection {
        var url = initial
        repeat(6) {
            currentCoroutineContext().ensureActive()
            if (!sources.allowsConnection(url.toURI())) throw UpdateFailure("更新地址不受信任，已停止连接")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "PengWa-Updater")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Cache-Control", "no-cache")
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }
            try {
                if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
                val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
                url = URL(url, location)
            } catch (e: Exception) {
                connection.disconnect()
                throw e
            }
            connection.disconnect()
        }
        throw IOException("Too many redirects")
    }
}
