package app.pengwa.update

import java.net.URI

/** Metadata is advisory; the downloaded APK must independently match the installed identity. */
data class UpdateRelease(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val size: Long,
    val sha256: String,
    val notes: String,
) {
    fun validate(expectedPackage: String) {
        require(packageName == expectedPackage && versionCode > 0)
        require(versionName.isNotBlank() && versionName.length <= 100 && minSdk > 0)
        require(size in 1..MAX_APK_BYTES && sha256.matches(Regex("[a-fA-F0-9]{64}")))
        val uri = URI(apkUrl)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null)
        require(uri.path.startsWith("/xh321/PengWa/releases/download/") && uri.path.endsWith(".apk"))
    }

    fun isNewerThan(installed: Long, sdk: Int) = versionCode > installed && minSdk <= sdk

    companion object { const val MAX_APK_BYTES = 150L * 1024 * 1024 }
}
