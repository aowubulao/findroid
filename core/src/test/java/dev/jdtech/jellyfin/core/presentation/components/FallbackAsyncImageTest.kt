package dev.jdtech.jellyfin.core.presentation.components

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class FallbackAsyncImageTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun failedBackgroundRequestsFallBackToTheCoverAndStopAfterSuccess() {
        val backdrop = Uri.parse("https://jellyfin.example/missing-backdrop")
        val cover = Uri.parse("https://jellyfin.example/cover")
        val unused = Uri.parse("https://jellyfin.example/unused-parent-cover")
        val attempts = CopyOnWriteArrayList<String>()
        val loader = imageLoader(attempts, successfulImage = cover)

        compose.setContent {
            FallbackAsyncImage(
                imageUris = listOf(backdrop, cover, unused),
                contentDescription = "background",
                modifier = Modifier.size(100.dp),
                imageLoader = loader,
            )
        }

        waitForImages { attempts.size >= 2 }
        compose.runOnIdle { assertEquals(listOf(backdrop.toString(), cover.toString()), attempts) }
    }

    @Test
    fun allFailedImagesAreTriedOnlyOnceAndNewItemStartsFromItsFirstImage() {
        val first = Uri.parse("https://jellyfin.example/first")
        val second = Uri.parse("https://jellyfin.example/second")
        val nextItem = Uri.parse("https://jellyfin.example/next-item")
        val images = mutableStateOf(listOf(first, first, second))
        val attempts = CopyOnWriteArrayList<String>()
        val loader = imageLoader(attempts)

        compose.setContent {
            FallbackAsyncImage(
                imageUris = images.value,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                imageLoader = loader,
            )
        }

        waitForImages { attempts.size >= 2 }
        compose.runOnIdle {
            assertEquals(listOf(first.toString(), second.toString()), attempts)
            images.value = listOf(nextItem)
        }
        waitForImages { attempts.size >= 3 }
        compose.runOnIdle {
            assertEquals(listOf(first.toString(), second.toString(), nextItem.toString()), attempts)
        }
    }

    @Test
    fun missingOfflineBackgroundFallsBackToAnExistingCoverInTheFilesDirectory() {
        val coverFile = File(context.filesDir, "images/offline/primary")
        coverFile.parentFile!!.mkdirs()
        coverFile.outputStream().use {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val backdrop = Uri.Builder().appendEncodedPath("images/offline/backdrop").build()
        val cover = Uri.Builder().appendEncodedPath("images/offline/primary").build()
        val attempts = CopyOnWriteArrayList<String>()
        val results = CopyOnWriteArrayList<Boolean>()
        val loader =
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            attempts.add(chain.request.data.toString())
                            chain.proceed().also { results.add(it is SuccessResult) }
                        }
                    )
                }
                .build()

        compose.setContent {
            FallbackAsyncImage(
                imageUris = listOf(backdrop, cover),
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                imageLoader = loader,
            )
        }

        waitForImages { results.size >= 2 }
        compose.runOnIdle {
            assertEquals(
                listOf(
                    Uri.fromFile(File(coverFile.parentFile, "backdrop")).toString(),
                    Uri.fromFile(coverFile).toString(),
                ),
                attempts,
            )
            assertEquals(listOf(false, true), results)
        }
    }

    @Test
    fun emptyImageListDoesNotRequestTheFilesDirectory() {
        val attempts = CopyOnWriteArrayList<String>()
        val loader = imageLoader(attempts)

        compose.setContent {
            FallbackAsyncImage(
                imageUris = emptyList(),
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                imageLoader = loader,
            )
        }

        compose.runOnIdle { assertEquals(emptyList<String>(), attempts) }
    }

    private fun waitForImages(condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 10_000) {
            // Flush recompositions after Coil completes work on its real I/O dispatcher.
            compose.waitForIdle()
            condition()
        }
    }

    private fun imageLoader(
        attempts: MutableList<String>,
        successfulImage: Uri? = null,
    ): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    Interceptor { chain ->
                        attempts.add(chain.request.data.toString())
                        if (chain.request.data == successfulImage) {
                            SuccessResult(
                                image = ColorImage(android.graphics.Color.RED),
                                request = chain.request,
                            )
                        } else {
                            ErrorResult(
                                image = null,
                                request = chain.request,
                                throwable = IOException("Missing image"),
                            )
                        }
                    }
                )
            }
            .build()
}
