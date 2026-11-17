package dev.jdtech.jellyfin.core.update

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSigningInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UpdateApkTest {
    private val update =
        AppUpdate(
            "new",
            39,
            "",
            "arm64-v8a",
            "",
            3,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        )

    @Test
    fun streamedDownloadRequiresTheExpectedSizeAndDigest() {
        val bytes = "abc".toByteArray()
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Int>()
        copyVerifiedApk(bytes.inputStream(), output, update) { progress.add(it) }
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(listOf(100), progress)
        for (corrupt in listOf("ab", "abd", "abcd")) {
            assertThrows(InvalidUpdateApk::class.java) {
                copyVerifiedApk(corrupt.byteInputStream(), null, update) {}
            }
        }
    }

    @Test
    fun installerOnlyAcceptsANewerPackageWithTheExpectedIdentityVersionAndSigners() {
        val installed = packageInfo(38)
        verifyUpdatePackage(packageInfo(39), installed, update)
        for (invalid in
            listOf(
                null,
                packageInfo(38),
                packageInfo(40),
                packageInfo(39).apply { versionName = "other" },
                packageInfo(39).apply { packageName = "other.app" },
                packageInfo(39).apply { applicationInfo!!.minSdkVersion = 33 },
                packageInfo(39, signer = 2),
                packageInfo(39).apply { signingInfo = null },
            )) {
            assertThrows(InvalidUpdateApk::class.java) {
                verifyUpdatePackage(invalid, installed, update)
            }
        }
        assertThrows(InvalidUpdateApk::class.java) {
            verifyUpdatePackage(packageInfo(39), packageInfo(39), update)
        }
    }

    private fun packageInfo(code: Long, signer: Byte = 1) =
        PackageInfo().apply {
            packageName = "dev.jdtech.jellyfin.compat"
            versionName = "new"
            longVersionCode = code
            applicationInfo = ApplicationInfo().apply { minSdkVersion = 28 }
            signingInfo =
                SigningInfo().also {
                    Shadow.extract<ShadowSigningInfo>(it)
                        .setSignatures(arrayOf(Signature(byteArrayOf(signer))))
                }
        }
}
