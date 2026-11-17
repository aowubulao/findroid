package dev.jdtech.jellyfin.core.update

import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubReleasesTest {
    private val phone =
        UpdateTarget(
            DeviceType.PHONE,
            "dev.jdtech.jellyfin.compat",
            35,
            listOf("arm64-v8a", "armeabi-v7a"),
            29,
        )
    private val tv = phone.copy(deviceType = DeviceType.TV)

    @Test
    fun phoneAndTvChooseTheirOwnNewestPublishedCompatibleApk() = runBlocking {
        val both = Release(37, listOf(Apk("phone"), Apk("tv")))
        val phoneOnly = Release(40, listOf(Apk("phone")), shape = "single")
        val draft = Release(41, listOf(Apk("phone"), Apk("tv")), draft = true)
        val old = Release(36, listOf(Apk("phone"), Apk("tv")), shape = "apks")
        val releases = catalog(both, draft, old, phoneOnly)
        val phoneUpdate = releases.findUpdate(phone)!!
        assertEquals(phoneOnly.url(Apk("phone").name), phoneUpdate.url)
        assertEquals(40L, phoneUpdate.versionCode)
        assertEquals("Release notes for 40", phoneUpdate.notes)
        assertEquals(both.url(Apk("tv").name), releases.findUpdate(tv)!!.url)
        assertEquals(36L, catalog(old).findUpdate(tv)!!.versionCode)
        assertNull(releases.findUpdate(tv.copy(installedVersion = 37)))
        assertNull(releases.findUpdate(phone.copy(installedVersion = 42)))
    }

    @Test
    fun filtersPackageSdkAndAbiAndPrefersTheDevicesFirstSupportedAbi() = runBlocking {
        val dualAbi = Release(39, listOf(Apk("phone", "armeabi-v7a"), Apk("phone")))
        val wrongPackage =
            Release(42, listOf(Apk("phone", packageName = "dev.jdtech.jellyfin.debug")))
        val needsNewAndroid = Release(43, listOf(Apk("phone", minSdk = 33)))
        val unsupportedAbi = Release(44, listOf(Apk("phone", "x86_64")))
        val releases = catalog(unsupportedAbi, wrongPackage, dualAbi, needsNewAndroid)
        assertEquals(dualAbi.url(Apk("phone").name), releases.findUpdate(phone)!!.url)
        assertNull(catalog(dualAbi).findUpdate(tv))
        assertNull(
            catalog(dualAbi).findUpdate(phone.copy(applicationId = "dev.jdtech.jellyfin.debug"))
        )
    }

    @Test
    fun paginatesPastDraftsAndPropagatesNetworkFailures() {
        val first = Release(36, listOf(Apk("tv")), draft = true)
        val last = Release(37, listOf(Apk("tv")))
        val pages = mutableListOf<String>()
        val releases = GitHubReleases { url ->
            when {
                url.endsWith("page=1") -> List(100) { first.listing() }.joinToString(",", "[", "]")
                url.endsWith("page=2") -> "[${last.listing()}]"
                url == last.url("release-verification.json") -> last.report()
                else -> error(url)
            }.also { pages.add(url) }
        }
        assertEquals(37L, runBlocking { releases.findUpdate(tv) }!!.versionCode)
        assertEquals(3, pages.size)
        val offline = GitHubReleases { throw IOException("unavailable") }
        assertThrows(IOException::class.java) { runBlocking { offline.findUpdate(tv) } }
    }

    private data class Apk(
        val variant: String,
        val abi: String = "arm64-v8a",
        val minSdk: Int = 28,
        val packageName: String = "dev.jdtech.jellyfin.compat",
    ) {
        val name = "findroid-$variant-$abi-release.apk"

        fun report(code: Int) =
            """
            {"apk":"$name","abi":"$abi","minimum_sdk":$minSdk,"application_id":"$packageName",
             "version_code":$code,"version_name":"build-$code","sha256":"${"a".repeat(64)}"}
        """
                .trimIndent()
    }

    private class Release(
        val code: Int,
        val apks: List<Apk>,
        val shape: String = "artifacts",
        val draft: Boolean = false,
    ) {
        fun url(name: String) =
            "https://github.com/aowubulao/findroid/releases/download/v$code/$name"

        fun listing(): String {
            val assets =
                (apks.map { it.name } + "release-verification.json").joinToString {
                    """{"name":"$it","size":100,"browser_download_url":"${url(it)}"}"""
                }
            return """{"prerelease":true,"draft":$draft,"body":"Release notes for $code","assets":[$assets]}"""
        }

        fun report(): String =
            if (shape == "single") apks.single().report(code)
            else """{"$shape":[${apks.joinToString { it.report(code) }}]}"""
    }

    private fun catalog(vararg releases: Release) = GitHubReleases { url ->
        if (url == "https://api.github.com/repos/aowubulao/findroid/releases?per_page=100&page=1")
            releases.joinToString(",", "[", "]") { it.listing() }
        else releases.single { it.url("release-verification.json") == url }.report()
    }
}
