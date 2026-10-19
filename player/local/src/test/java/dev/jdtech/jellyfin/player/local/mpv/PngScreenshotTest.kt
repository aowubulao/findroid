package dev.jdtech.jellyfin.player.local.mpv

import java.io.File
import java.io.IOException
import java.util.Base64
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PngScreenshotTest {
    @get:Rule val directory = TemporaryFolder()

    private val png =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1kAAAAASUVORK5CYII="
            )

    @Test
    fun acceptsCompletedPng() {
        verifyPngScreenshot(frame(png))
    }

    @Test
    fun rejectsEmptyNativeOutput() {
        assertThrows(IOException::class.java) { verifyPngScreenshot(frame(byteArrayOf())) }
    }

    @Test
    fun rejectsTruncatedNativeOutput() {
        assertThrows(IOException::class.java) {
            verifyPngScreenshot(frame(png.dropLast(4).toByteArray()))
        }
    }

    @Test
    fun rejectsNonPngOutput() {
        val invalid = png.copyOf().apply { this[0] = 0 }
        assertThrows(IOException::class.java) { verifyPngScreenshot(frame(invalid)) }
    }

    @Test
    fun rejectsMissingNativeOutput() {
        assertThrows(IOException::class.java) {
            verifyPngScreenshot(File(directory.root, "missing.png"))
        }
    }

    private fun frame(data: ByteArray) = directory.newFile().apply { writeBytes(data) }
}
