package dev.jdtech.jellyfin.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.media3.common.Player
import dev.jdtech.jellyfin.player.local.mpv.MPVPlayer
import java.io.File
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Captures the playing video, never a Window, the controller hierarchy, or a server thumbnail. */
internal object VideoFrameCapture {
    suspend fun capture(context: Context, player: Player, videoSurface: View?): File {
        val file = File.createTempFile("findroid-frame-", ".png", context.cacheDir)
        try {
            if (player is MPVPlayer) {
                // mpv owns subtitle rendering too. Its native "video" capture omits subtitles/OSD.
                withContext(Dispatchers.IO) { player.captureVideoFrame(file) }
            } else {
                val surface =
                    videoSurface as? SurfaceView
                        ?: throw IOException("The player has no capturable video surface")
                val bitmap =
                    try {
                        withTimeout(5000) { copySurface(surface) }
                    } catch (error: TimeoutCancellationException) {
                        throw IOException("Timed out capturing the video frame", error)
                    }
                try {
                    withContext(Dispatchers.IO) {
                        file.outputStream().use { output ->
                            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                throw IOException("Could not encode the video frame")
                            }
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            currentCoroutineContext().ensureActive()
            return file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    private suspend fun copySurface(surface: SurfaceView): Bitmap =
        suspendCancellableCoroutine { continuation ->
            if (!surface.isAttachedToWindow || !surface.holder.surface.isValid) {
                continuation.resumeWithException(IOException("The video surface is unavailable"))
                return@suspendCancellableCoroutine
            }
            val (width, height) = frameCaptureSize(surface.width, surface.height)
            val bitmap = createBitmap(width, height)
            try {
                PixelCopy.request(
                    surface,
                    bitmap,
                    { result ->
                        // Do not recycle before this callback: PixelCopy may still be writing.
                        if (result == PixelCopy.SUCCESS) {
                            continuation.resume(bitmap) { _, value, _ -> value.recycle() }
                        } else {
                            bitmap.recycle()
                            continuation.resumeWithException(
                                IOException("Video frame copy failed: $result")
                            )
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            } catch (error: Exception) {
                bitmap.recycle()
                continuation.resumeWithException(error)
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun saveToGallery(context: Context, file: File, displayName: String): Uri =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Findroid",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            val uri =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Could not create the gallery image")
            try {
                writeFile(context, file, uri)
                currentCoroutineContext().ensureActive()
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                if (resolver.update(uri, values, null, null) != 1) {
                    throw IOException("Could not publish the gallery image")
                }
                uri
            } catch (error: Throwable) {
                // A failed/cancelled save must not leave a blank or permanently pending image.
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
        }

    suspend fun saveToDocument(context: Context, file: File, uri: Uri) {
        try {
            withContext(Dispatchers.IO) {
                writeFile(context, file, uri)
                currentCoroutineContext().ensureActive()
            }
        } catch (error: Throwable) {
            // The picker already created this document, even if IO dispatch was cancelled.
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            }
            throw error
        }
    }

    private fun writeFile(context: Context, file: File, uri: Uri) {
        val output =
            context.contentResolver.openOutputStream(uri, "w")
                ?: throw IOException("Could not open the image destination")
        output.use { destination -> file.inputStream().use { it.copyTo(destination) } }
    }
}
