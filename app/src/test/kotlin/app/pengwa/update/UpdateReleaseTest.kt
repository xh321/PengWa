package app.pengwa.update

import kotlin.test.*
import org.junit.Test

class UpdateReleaseTest {
    private val release = UpdateRelease("app.pengwa", 2, "0.2.0", 29,
        "https://github.com/xh321/PengWa/releases/download/v0.2.0/pengwa.apk", 1024, "a".repeat(64), "")

    @Test fun onlyNewerCompatibleVersionIsOffered() {
        release.validate("app.pengwa")
        assertTrue(release.isNewerThan(1, 29))
        assertFalse(release.isNewerThan(2, 29))
        assertFalse(release.isNewerThan(3, 29))
        assertFalse(release.isNewerThan(1, 28))
    }

    @Test fun rejectsOtherChannelsAndMalformedMetadata() {
        assertFailsWith<IllegalArgumentException> { release.validate("app.pengwa.debug") }
        listOf(release.copy(versionCode = 0), release.copy(minSdk = 0), release.copy(size = 0),
            release.copy(size = UpdateRelease.MAX_APK_BYTES + 1), release.copy(sha256 = "invalid"),
            release.copy(versionName = "")).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { invalid.validate("app.pengwa") }
        }
    }

    @Test fun rejectsUntrustedDownloadUrls() {
        listOf("http://github.com/xh321/PengWa/releases/download/v2/app.apk",
            "https://github.com.evil.test/xh321/PengWa/releases/download/v2/app.apk",
            "https://evil.test/xh321/PengWa/releases/download/v2/app.apk",
            "https://github.com/other/PengWa/releases/download/v2/app.apk",
            "https://user@github.com/xh321/PengWa/releases/download/v2/app.apk",
            "https://github.com:444/xh321/PengWa/releases/download/v2/app.apk").forEach { url ->
            assertFailsWith<IllegalArgumentException>(url) { release.copy(apkUrl = url).validate("app.pengwa") }
        }
    }
}
