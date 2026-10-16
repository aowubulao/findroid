import org.gradle.jvm.toolchain.JavaLanguageVersion

object Versions {
    // Keep this increasing across compatibility revisions and upstream version changes.
    const val APP_CODE = 34
    // <upstream version>-jf10.10-<compatibility revision>; reset the revision after upstream merges.
    const val APP_NAME = "1.1.0-jf10.10-1.0"

    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36
    const val MIN_SDK = 28
    const val BUILD_TOOLS = "37.0.0"

    val JAVA = JavaLanguageVersion.of(17)
}
