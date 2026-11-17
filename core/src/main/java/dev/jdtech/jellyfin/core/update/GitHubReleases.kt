package dev.jdtech.jellyfin.core.update

import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

internal data class UpdateTarget(
    val deviceType: DeviceType,
    val applicationId: String,
    val installedVersion: Long,
    val supportedAbis: List<String>,
    val sdk: Int,
)

@Serializable
internal data class AppUpdate(
    val versionName: String,
    val versionCode: Long,
    val notes: String,
    val abi: String,
    val url: String,
    val size: Long,
    val sha256: String,
)

internal class GitHubReleases(private val fetchText: suspend (String) -> String) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findUpdate(target: UpdateTarget): AppUpdate? {
        val preferred =
            compareBy<AppUpdate> { it.versionCode }.thenBy { -target.supportedAbis.indexOf(it.abi) }
        var best: AppUpdate? = null
        var page = 1
        do {
            val releases =
                json.decodeFromString<List<Release>>(fetchText("$RELEASES?per_page=100&page=$page"))
            for (release in releases) {
                if (release.draft) continue
                val update = findApks(release, target).maxWithOrNull(preferred) ?: continue
                if (best == null || preferred.compare(update, best) > 0) best = update
            }
            page++
        } while (releases.size == 100)
        return best
    }

    private suspend fun findApks(release: Release, target: UpdateTarget): List<AppUpdate> {
        val variant = target.deviceType.name.lowercase()
        val assets =
            release.assets
                .filter { asset ->
                    target.supportedAbis.any { asset.name.endsWith("-$variant-$it-release.apk") }
                }
                .associateBy { it.name }
        if (assets.isEmpty()) return emptyList()

        val report =
            release.assets.firstOrNull { it.name == "release-verification.json" }
                ?: return emptyList()
        val document = json.parseToJsonElement(fetchText(report.downloadUrl())).jsonObject
        // Older releases used "apks", or a single APK report at the root.
        val entries = document["artifacts"] ?: document["apks"]
        val artifacts =
            if (entries == null) listOf(json.decodeFromJsonElement<Artifact>(document))
            else json.decodeFromJsonElement<List<Artifact>>(entries)

        return artifacts.mapNotNull { artifact ->
            val asset = assets[artifact.apk] ?: return@mapNotNull null
            if (
                artifact.applicationId != target.applicationId ||
                    artifact.versionCode <= target.installedVersion ||
                    artifact.minimumSdk > target.sdk ||
                    artifact.abi !in target.supportedAbis ||
                    !asset.name.endsWith("-$variant-${artifact.abi}-release.apk")
            )
                return@mapNotNull null

            require(SHA256.matches(artifact.sha256) && asset.size > 0)
            AppUpdate(
                artifact.versionName,
                artifact.versionCode,
                release.body.orEmpty().trim(),
                artifact.abi,
                asset.downloadUrl(),
                asset.size,
                artifact.sha256.lowercase(),
            )
        }
    }

    @Serializable
    private data class Release(
        val draft: Boolean,
        val body: String? = null,
        val assets: List<Asset>,
    )

    @Serializable
    private data class Asset(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url") val url: String,
    ) {
        fun downloadUrl(): String {
            require(url.startsWith("https://github.com/aowubulao/findroid/releases/download/"))
            return url
        }
    }

    @Serializable
    private data class Artifact(
        val apk: String,
        @SerialName("application_id") val applicationId: String,
        @SerialName("version_code") val versionCode: Long,
        @SerialName("version_name") val versionName: String,
        @SerialName("minimum_sdk") val minimumSdk: Int,
        val abi: String,
        val sha256: String,
    )

    companion object {
        private const val RELEASES = "https://api.github.com/repos/aowubulao/findroid/releases"
        private val SHA256 = Regex("[0-9a-fA-F]{64}")
    }
}
