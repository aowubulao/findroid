import org.junit.Assert.assertThrows
import org.junit.Test

class CompatibilityReleaseConfigTest {
    private val release =
        CompatibilityReleaseConfig(
            applicationId = "dev.jdtech.jellyfin.compat",
            versionName = Versions.APP_NAME,
            versionCode = Versions.APP_CODE,
            debuggable = false,
            minifyEnabled = true,
            shrinkResources = true,
            hasSigningConfig = false,
        )

    @Test fun acceptsIndependentOptimizedRelease() = release.validate()

    @Test
    fun rejectsOfficialOrDebugIdentity() {
        for (id in listOf("dev.jdtech.jellyfin", "dev.jdtech.jellyfin.debug")) {
            assertThrows(IllegalArgumentException::class.java) {
                release.copy(applicationId = id).validate()
            }
        }
    }

    @Test
    fun rejectsVersionWithoutCompatibilityMarker() {
        assertThrows(IllegalArgumentException::class.java) {
            release.copy(versionName = "1.1.0").validate()
        }
    }

    @Test
    fun rejectsOldVersionCode() {
        assertThrows(IllegalArgumentException::class.java) {
            release.copy(versionCode = 34).validate()
        }
    }

    @Test
    fun rejectsDebuggableRelease() {
        assertThrows(IllegalArgumentException::class.java) {
            release.copy(debuggable = true).validate()
        }
    }

    @Test
    fun rejectsDisabledMinification() {
        assertThrows(IllegalArgumentException::class.java) {
            release.copy(minifyEnabled = false).validate()
        }
    }

    @Test
    fun rejectsDisabledResourceShrinking() {
        assertThrows(IllegalArgumentException::class.java) {
            release.copy(shrinkResources = false).validate()
        }
    }

    @Test
    fun rejectsAnyGradleSigningConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            release.copy(hasSigningConfig = true).validate()
        }
    }
}
