package app.pengwa.update

import android.content.Context
import android.content.pm.PackageManager
import app.pengwa.BuildConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL

class UpdateFailure(message: String) : IOException(message)

/** No background polling: network access happens only after tapping the update controls. */
internal class UpdateRepository(private val context: Context) {
    private val sources = UpdateSources(BuildConfig.UPDATE_PROXY_BASE_URL)
    private val http = UpdateHttpClient(sources)

    suspend fun check(source: UpdateSource, onSource: (String) -> Unit): UpdateRelease? {
        val endpoint = if (BuildConfig.DEBUG) "download/nightly" else "latest/download"
        val endpoints = sources.endpoints("https://github.com/xh321/PengWa/releases/$endpoint/update.json", source)
        var failure: IOException? = null
        for ((index, candidate) in endpoints.withIndex()) {
            currentCoroutineContext().ensureActive()
            onSource(candidate.label)
            try {
                val release = readRelease(candidate)
                if (release.versionCode > BuildConfig.VERSION_CODE && release.minSdk > android.os.Build.VERSION.SDK_INT) {
                    throw UpdateFailure("新版本需要 Android API ${release.minSdk} 或更高版本")
                }
                if (release.isNewerThan(BuildConfig.VERSION_CODE.toLong(), android.os.Build.VERSION.SDK_INT)) return release
                // A mirror can lag. In Auto mode, confirm an apparently current build against GitHub.
                if (index == endpoints.lastIndex) return null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: IOException) { failure = e }
        }
        throw failure ?: UpdateFailure("无法确认最新版本，请切换线路后重试")
    }

    private suspend fun readRelease(endpoint: UpdateEndpoint): UpdateRelease {
        val connection = http.connect(URL(endpoint.url))
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
            return try {
                val json = JSONObject(bytes.toString(Charsets.UTF_8))
                UpdateRelease(json.getString("packageName"), json.getLong("versionCode"),
                    json.getString("versionName"), json.getInt("minSdk"), json.getString("apkUrl"),
                    json.getLong("size"), json.getString("sha256"), json.optString("notes").take(4000))
                    .also { it.validate(context.packageName) }
            } catch (e: Exception) { throw UpdateFailure("更新信息无效，请切换线路后重试") }
        } finally { connection.disconnect() }
    }

    suspend fun download(release: UpdateRelease, source: UpdateSource, onProgress: (DownloadProgress) -> Unit): File {
        release.validate(context.packageName)
        val directory = File(context.cacheDir, "updates")
        val partial = ResumableUpdateDownload { url, offset -> http.connect(url, offset) }
            .download(release, sources.endpoints(release.apkUrl, source), directory, onProgress)
        try { verifyApk(partial, release) }
        catch (e: Exception) { partial.delete(); throw e }
        val apk = File(directory, "update.apk")
        apk.delete()
        if (!partial.renameTo(apk)) throw IOException("Cannot save APK")
        return apk
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

}
