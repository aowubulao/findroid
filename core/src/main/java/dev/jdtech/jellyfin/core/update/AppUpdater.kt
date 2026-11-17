package dev.jdtech.jellyfin.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

@Singleton
internal class AppUpdater @Inject constructor(@ApplicationContext private val context: Context) {
    private val client = OkHttpClient.Builder().followSslRedirects(false).build()
    private val releases = GitHubReleases { url -> get(url) { it.string() } }
    private val downloadLock = Mutex()
    private val directory = File(context.cacheDir, "app-updates")

    @Suppress("DEPRECATION")
    suspend fun findUpdate(deviceType: DeviceType): AppUpdate? =
        withContext(Dispatchers.IO) {
            val installed = context.packageManager.getPackageInfo(context.packageName, 0)
            directory.listFiles()?.forEach { file ->
                val code = file.name.substringBefore('-').toLongOrNull()
                if (code != null && code <= installed.longVersionCode) file.delete()
            }
            releases.findUpdate(
                UpdateTarget(
                    deviceType,
                    installed.packageName,
                    installed.longVersionCode,
                    Build.SUPPORTED_ABIS.toList(),
                    Build.VERSION.SDK_INT,
                )
            )
        }

    @Suppress("DEPRECATION")
    suspend fun download(update: AppUpdate, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            downloadLock.withLock {
                val pm = context.packageManager
                // Some Android versions only collect archive certificates for GET_SIGNATURES.
                // Request both flags to populate SigningInfo with the verified current signers.
                val archiveFlags =
                    PackageManager.GET_SIGNATURES or PackageManager.GET_SIGNING_CERTIFICATES
                val installed =
                    pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                if (installed.longVersionCode >= update.versionCode) return@withLock null
                val apk = File(directory, "${update.versionCode}-${update.sha256}.apk")
                if (apk.isFile) {
                    try {
                        apk.inputStream().use { copyVerifiedApk(it, null, update, onProgress) }
                        verifyUpdatePackage(
                            pm.getPackageArchiveInfo(apk.path, archiveFlags),
                            installed,
                            update,
                        )
                        return@withLock apk
                    } catch (_: InvalidUpdateApk) {
                        apk.delete()
                    }
                }

                // Only the verified, atomically moved APK is reachable through the provider.
                Files.createDirectories(directory.toPath())
                val partial = File(context.cacheDir, "findroid-update.partial.apk")
                try {
                    get(update.url) { body ->
                        partial.outputStream().buffered().use { output ->
                            copyVerifiedApk(body.byteStream(), output, update, onProgress)
                        }
                    }
                    verifyUpdatePackage(
                        pm.getPackageArchiveInfo(partial.path, archiveFlags),
                        installed,
                        update,
                    )
                    Files.move(
                        partial.toPath(),
                        apk.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    apk
                } finally {
                    partial.delete()
                }
            }
        }

    private suspend fun <T> get(url: String, read: (ResponseBody) -> T): T = coroutineScope {
        val call =
            client.newCall(Request.Builder().url(url).header("User-Agent", "Findroid").build())
        try {
            async(Dispatchers.IO) {
                    call.execute().use { response ->
                        if (!response.isSuccessful)
                            throw IOException("GitHub HTTP ${response.code}")
                        read(response.body)
                    }
                }
                .await()
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        }
    }
}

internal class InvalidUpdateApk : IOException("APK verification failed")

internal fun copyVerifiedApk(
    input: InputStream,
    output: OutputStream?,
    update: AppUpdate,
    onProgress: (Int) -> Unit,
) {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var written = 0L
    var previousPercent = -1
    while (true) {
        val count = input.read(buffer)
        if (count == -1) break
        written += count
        if (written > update.size) throw InvalidUpdateApk()
        digest.update(buffer, 0, count)
        output?.write(buffer, 0, count)
        val percent = (written * 100 / update.size).toInt()
        if (percent != previousPercent) {
            previousPercent = percent
            onProgress(percent)
        }
    }
    if (written != update.size || digest.digest().toHexString() != update.sha256)
        throw InvalidUpdateApk()
}

internal fun verifyUpdatePackage(archive: PackageInfo?, installed: PackageInfo, update: AppUpdate) {
    val signers = installed.signingInfo?.apkContentsSigners?.toSet().orEmpty()
    if (
        archive == null ||
            archive.packageName != installed.packageName ||
            archive.longVersionCode != update.versionCode ||
            archive.longVersionCode <= installed.longVersionCode ||
            archive.versionName != update.versionName ||
            (archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT ||
            signers.isEmpty() ||
            archive.signingInfo?.apkContentsSigners?.toSet() != signers
    )
        throw InvalidUpdateApk()
}

class UpdateFileProvider : FileProvider() {
    companion object {
        internal fun installIntent(context: Context, apk: File): Intent {
            val uri = getUriForFile(context, "${context.packageName}.updates", apk)
            return Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
