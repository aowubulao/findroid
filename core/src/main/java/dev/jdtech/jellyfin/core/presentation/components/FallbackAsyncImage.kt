package dev.jdtech.jellyfin.core.presentation.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import java.io.File

/** Tries each image once, including when an advertised image or an offline file is missing. */
@Composable
fun FallbackAsyncImage(
    imageUris: List<Uri>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: Painter? = null,
    contentScale: ContentScale = ContentScale.Fit,
    imageLoader: ImageLoader = SingletonImageLoader.get(LocalContext.current),
) {
    val context = LocalContext.current
    val models =
        remember(imageUris, context.filesDir) {
            imageUris
                .mapNotNull { uri ->
                    if (uri.scheme == null) {
                        uri.path
                            ?.takeIf { it.isNotBlank() }
                            ?.let { Uri.fromFile(File(context.filesDir, it)) }
                    } else uri
                }
                .distinct()
        }
    var imageIndex by remember(models) { mutableIntStateOf(0) }
    val model = models.getOrNull(imageIndex)

    AsyncImage(
        model = model,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        modifier = modifier,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        contentScale = contentScale,
        onError = { state ->
            // Only advance for the current request, even if an older request finishes late.
            if (
                model != null &&
                    state.result.request.data == model &&
                    models.getOrNull(imageIndex) == model
            ) {
                imageIndex++
            }
        },
    )
}
