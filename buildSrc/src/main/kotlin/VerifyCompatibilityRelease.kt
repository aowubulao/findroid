import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Release artifacts are signed offline; no private key or password enters Gradle. */
abstract class VerifyCompatibilityRelease : DefaultTask() {
    @get:Input abstract val applicationId: Property<String>
    @get:Input abstract val versionName: Property<String>
    @get:Input abstract val versionCode: Property<Int>
    @get:Input abstract val debuggable: Property<Boolean>
    @get:Input abstract val minifyEnabled: Property<Boolean>
    @get:Input abstract val shrinkResources: Property<Boolean>
    @get:Input abstract val hasSigningConfig: Property<Boolean>

    init {
        group = "verification"
        description = "Checks the independent compatibility Release identity and optimizations."
    }

    @TaskAction
    fun verify() {
        CompatibilityReleaseConfig(
                applicationId.get(),
                versionName.get(),
                versionCode.get(),
                debuggable.get(),
                minifyEnabled.get(),
                shrinkResources.get(),
                hasSigningConfig.get(),
            )
            .validate()
    }
}

internal data class CompatibilityReleaseConfig(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val debuggable: Boolean,
    val minifyEnabled: Boolean,
    val shrinkResources: Boolean,
    val hasSigningConfig: Boolean,
) {
    fun validate() {
        require(applicationId == "dev.jdtech.jellyfin.compat") {
            "Compatibility Release must use its independent application ID."
        }
        require(versionName.matches(Regex("\\d+\\.\\d+\\.\\d+-jf10\\.10-\\d+\\.\\d+"))) {
            "Compatibility Release must use the documented version naming scheme."
        }
        require(versionCode >= 35) { "Compatibility Release versionCode must be at least 35." }
        require(!debuggable) { "Compatibility Release must not be debuggable." }
        require(minifyEnabled) { "Compatibility Release must enable R8 optimization." }
        require(shrinkResources) { "Compatibility Release must enable resource shrinking." }
        require(!hasSigningConfig) {
            "Compatibility Release must remain unsigned in Gradle for dedicated offline signing."
        }
    }
}
