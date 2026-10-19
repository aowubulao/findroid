package dev.jdtech.jellyfin.player.local.mpv

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** The JNI command API drops mpv's status, so reject empty/truncated output before exporting it. */
internal fun verifyPngScreenshot(file: File) {
    val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    val end =
        byteArrayOf(0, 0, 0, 0, 0x49, 0x45, 0x4e, 0x44, 0xae.toByte(), 0x42, 0x60, 0x82.toByte())
    RandomAccessFile(file, "r").use { input ->
        if (input.length() < signature.size + end.size) {
            throw IOException("mpv did not produce a complete PNG frame")
        }
        val actualSignature = ByteArray(signature.size)
        input.readFully(actualSignature)
        input.seek(input.length() - end.size)
        val actualEnd = ByteArray(end.size)
        input.readFully(actualEnd)
        if (!signature.contentEquals(actualSignature) || !end.contentEquals(actualEnd)) {
            throw IOException("mpv did not produce a complete PNG frame")
        }
    }
}
