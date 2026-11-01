package app.pengwa.update

import java.net.URI

/** Explicit choices also allow bypassing the third-party accelerator completely. */
enum class UpdateSource(val label: String) { Auto("自动"), Domestic("国内加速"), GitHub("GitHub 直连") }
data class UpdateEndpoint(val url: String, val label: String)

class UpdateSources(proxyBase: String) {
    val proxyOrigin: URI = URI(proxyBase)
    init {
        require(proxyOrigin.scheme == "https" && proxyOrigin.host != null && proxyOrigin.userInfo == null &&
            proxyOrigin.port == -1 && proxyOrigin.query == null && proxyOrigin.fragment == null &&
            proxyOrigin.path in listOf("", "/"))
    }
    val proxyHost: String get() = proxyOrigin.host

    fun endpoints(original: String, source: UpdateSource): List<UpdateEndpoint> {
        val uri = URI(original)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
            uri.rawPath.startsWith("/xh321/PengWa/releases/") && uri.query == null && uri.fragment == null)
        val direct = UpdateEndpoint(original, "GitHub 直连")
        val proxy = UpdateEndpoint("${proxyOrigin.toString().trimEnd('/')}/$original", "国内加速 · $proxyHost")
        return when (source) {
            UpdateSource.Auto -> listOf(proxy, direct)
            UpdateSource.Domestic -> listOf(proxy)
            UpdateSource.GitHub -> listOf(direct)
        }
    }

    fun allowsConnection(uri: URI): Boolean = uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
        uri.host in setOf(proxyHost, "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
}
