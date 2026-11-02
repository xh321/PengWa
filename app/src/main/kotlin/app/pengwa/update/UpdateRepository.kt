package app.pengwa.update

import android.content.Context
import android.content.pm.PackageManager
import app.pengwa.BuildConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class UpdateFailure(message: String) : IOException(message)

/** No background polling: network access happens only after tapping the update controls. */
class UpdateRepository(private val context: Context) {
    suspend fun check(): UpdateRelease? {
        val endpoint = if (BuildConfig.DEBUG) "download/nightly" else "latest/download"
        val connection = connect(URL("https://github.com/xh321/PengWa/releases/$endpoint/update.json"))
        try {
            if (connection.responseCode == 404) throw UpdateFailure("此通道尚未发布支持应用内更新的版本，请稍后再试")
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (output.size() <= 64 * 1024) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size > 64 * 1024) throw UpdateFailure("更新信息过大，已停止检查")
            val release = try {
                val json = JSONObject(bytes.toString(Charsets.UTF_8))
                UpdateRelease(json.getString("packageName"), json.getLong("versionCode"),
                    json.getString("versionName"), json.getInt("minSdk"), json.getString("apkUrl"),
                    json.getLong("size"), json.getString("sha256"), json.optString("notes").take(4000))
                    .also { it.validate(context.packageName) }
            } catch (e: Exception) { throw UpdateFailure("更新信息无效，请稍后重试") }
            if (release.versionCode > BuildConfig.VERSION_CODE && release.minSdk > android.os.Build.VERSION.SDK_INT) {
                throw UpdateFailure("新版本需要 Android API ${release.minSdk} 或更高版本")
            }
            return release.takeIf { it.isNewerThan(BuildConfig.VERSION_CODE.toLong(), android.os.Build.VERSION.SDK_INT) }
        } finally { connection.disconnect() }
    }

    suspend fun download(release: UpdateRelease, onProgress: (Float) -> Unit): File {
        release.validate(context.packageName)
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(directory, "update.part.apk")
        val apk = File(directory, "update.apk")
        apk.delete()
        val connection = connect(URL(release.apkUrl))
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            var total = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > release.size) throw UpdateFailure("安装包大小不符，已停止下载")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(total.toFloat() / release.size)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (total != release.size || !hash.equals(release.sha256, ignoreCase = true)) {
                throw UpdateFailure("安装包校验失败，请重新下载")
            }
            verifyApk(partial, release)
            if (!partial.renameTo(apk)) throw IOException("Cannot save APK")
            return apk
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    @Suppress("DEPRECATION")
    fun verifyApk(file: File, release: UpdateRelease) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw UpdateFailure("无法读取安装包，请重新下载")
        if (archive.packageName != context.packageName || archive.longVersionCode != release.versionCode ||
            archive.longVersionCode <= BuildConfig.VERSION_CODE || archive.versionName != release.versionName ||
            archive.applicationInfo?.minSdkVersion != release.minSdk) {
            throw UpdateFailure("安装包的应用或版本信息不符，已阻止安装")
        }
        val current = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val installedSigners = current.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        val newSigners = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        if (installedSigners.isNullOrEmpty() || installedSigners != newSigners) {
            throw UpdateFailure("安装包签名与当前应用不同，无法保留数据覆盖更新。请联系发布者使用固定签名")
        }
    }

    private suspend fun connect(initial: URL): HttpURLConnection {
        var url = initial
        repeat(6) {
            currentCoroutineContext().ensureActive()
            if (url.protocol != "https" || url.userInfo != null || url.port !in listOf(-1, 443) ||
                url.host !in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) {
                throw UpdateFailure("更新地址不受信任，已停止连接")
            }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "PengWa/${BuildConfig.VERSION_NAME}")
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
