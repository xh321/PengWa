package app.pengwa.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class DownloadProgress(val bytes: Long, val total: Long, val source: String)

/** Range data is advisory. Every complete file is hashed before the Android APK identity checks. */
internal class ResumableUpdateDownload(
    private val connect: suspend (URL, Long) -> HttpURLConnection,
) {
    suspend fun download(
        release: UpdateRelease,
        endpoints: List<UpdateEndpoint>,
        directory: File,
        onProgress: (DownloadProgress) -> Unit,
    ): File {
        require(endpoints.isNotEmpty())
        directory.mkdirs()
        val partial = File(directory, "${release.sha256.lowercase()}.part.apk")
        // A changed release cannot reuse old bytes. Bound disk use to one partial file and one installed candidate.
        directory.listFiles()?.filter { it.name.endsWith(".part.apk") && it != partial }?.forEach { it.delete() }
        if (partial.length() > release.size) partial.delete()
        var failure: IOException? = null
        for (endpoint in endpoints) {
            for (attempt in 0..1) {
                val beforeAttempt = partial.length()
                currentCoroutineContext().ensureActive()
                try {
                    onProgress(DownloadProgress(partial.length(), release.size, endpoint.label))
                    if (partial.length() < release.size) transfer(release, endpoint, partial, onProgress)
                    if (partial.length() != release.size) throw IOException("Incomplete download")
                    if (!hashMatches(partial, release.sha256)) {
                        partial.delete()
                        throw UpdateFailure("安装包校验失败，请重试下载或切换线路")
                    }
                    return partial
                } catch (e: CancellationException) { throw e }
                catch (e: IOException) {
                    failure = e
                    if (endpoint != endpoints.last() && partial.length() == beforeAttempt) break
                }
            }
        }
        throw UpdateFailure(if (failure is UpdateFailure) failure!!.message.orEmpty()
            else "下载中断，已保留进度。请重试下载或切换线路")
    }

    private suspend fun transfer(
        release: UpdateRelease, endpoint: UpdateEndpoint, partial: File, onProgress: (DownloadProgress) -> Unit,
    ) {
        val offset = partial.length()
        val connection = connect(URL(endpoint.url), offset)
        try {
            val append = when (connection.responseCode) {
                200 -> false // Range ignored: truncate, never append a complete response to existing bytes.
                206 -> {
                    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(connection.getHeaderField("Content-Range").orEmpty())
                    if (match == null || match.groupValues[1].toLongOrNull() != offset ||
                        match.groupValues[2].toLongOrNull() != release.size - 1 ||
                        match.groupValues[3].toLongOrNull() != release.size) {
                        partial.delete()
                        throw UpdateFailure("下载线路返回的续传信息无效，请重试或切换线路")
                    }
                    true
                }
                416 -> { partial.delete(); throw IOException("Range no longer valid") }
                else -> throw IOException("HTTP ${connection.responseCode}")
            }
            if (!connection.getHeaderField("Content-Encoding").isNullOrBlank() &&
                !connection.getHeaderField("Content-Encoding").equals("identity", ignoreCase = true)) {
                throw IOException("Encoded responses cannot be resumed")
            }
            var total = if (append) offset else 0L
            onProgress(DownloadProgress(total, release.size, endpoint.label))
            connection.inputStream.use { input ->
                java.io.FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (total + count > release.size) {
                            partial.delete()
                            throw UpdateFailure("安装包大小不符，请切换线路重新下载")
                        }
                        output.write(buffer, 0, count)
                        total += count
                        onProgress(DownloadProgress(total, release.size, endpoint.label))
                    }
                }
            }
        } finally { connection.disconnect() }
    }

    private suspend fun hashMatches(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)
    }
}
