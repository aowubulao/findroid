package dev.jdtech.jellyfin.core.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSigningInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UpdateInstallFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun restoredDownloadWaitsForSourcePermissionThenOpensTheVerifiedApk() {
        val activity = compose.activity
        val authority = "${activity.packageName}.updates"
        checkNotNull(activity.contentResolver.acquireContentProviderClient(authority)).close()
        val pm = shadowOf(activity.packageManager)
        val installed = pm.getInternalMutablePackageInfo(activity.packageName)
        installed.longVersionCode = 38
        installed.signingInfo =
            SigningInfo().also {
                Shadow.extract<ShadowSigningInfo>(it)
                    .setSignatures(arrayOf(Signature(byteArrayOf(1))))
            }
        val update =
            AppUpdate(
                "new",
                39,
                "",
                "arm64-v8a",
                "",
                3,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            )
        val file = File(activity.cacheDir, "app-updates/39-${update.sha256}.apk")
        file.parentFile!!.mkdirs()
        file.writeText("abc")
        pm.setPackageArchiveInfo(
            file.path,
            PackageInfo().apply {
                packageName = installed.packageName
                longVersionCode = 39
                versionName = "new"
                signingInfo = installed.signingInfo
                applicationInfo = installed.applicationInfo
            },
        )
        pm.setCanRequestPackageInstalls(false)

        val saved = SavedStateHandle(mapOf("pendingAppUpdate" to Json.encodeToString(update)))
        val model = AppUpdateViewModel(AppUpdater(activity.applicationContext), saved)
        compose.setContent { AppUpdateDialog(DeviceType.PHONE, onDismiss = {}, viewModel = model) }
        compose.waitUntil(10_000) { model.state.value is UpdateState.Ready }
        val allow = compose.onNodeWithText(activity.getString(R.string.app_update_allow))
        val shadowActivity = shadowOf(activity)
        allow.performClick()
        compose.runOnIdle {
            val request = shadowActivity.nextStartedActivityForResult.intent
            assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, request.action)
            assertEquals("package:${activity.packageName}", request.dataString)
            shadowActivity.receiveResult(request, Activity.RESULT_CANCELED, Intent())
        }
        compose.runOnIdle {
            assertFalse(shadowActivity.peekNextStartedActivity()?.action == Intent.ACTION_VIEW)
        }
        allow.performClick()
        compose.runOnIdle {
            val request = shadowActivity.nextStartedActivityForResult.intent
            pm.setCanRequestPackageInstalls(true)
            shadowActivity.receiveResult(request, Activity.RESULT_OK, Intent())
        }
        compose.waitUntil(10_000) {
            shadowActivity.peekNextStartedActivity()?.action == Intent.ACTION_VIEW
        }
        val intent = shadowActivity.nextStartedActivity
        assertEquals(UpdateFileProvider.installIntent(activity, file).data, intent.data)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals("content", intent.data!!.scheme)
        assertEquals(authority, intent.data!!.authority)
        activity.contentResolver.openInputStream(intent.data!!)!!.use {
            assertEquals("abc", it.bufferedReader().readText())
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateFileProvider.installIntent(
                activity,
                File(activity.cacheDir, "findroid-update.partial.apk"),
            )
        }
        model.close()
    }
}
