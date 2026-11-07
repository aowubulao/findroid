package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.components.FallbackAsyncImage
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.parallaxLayoutModifier

@Composable
fun ItemHeader(
    item: FindroidItem,
    scrollState: ScrollState,
    showLogo: Boolean = false,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    ItemHeaderBase(
        item = item,
        showLogo = showLogo,
        backdropImage = {
            FallbackAsyncImage(
                imageUris = item.images.backdropCandidates(),
                contentDescription = null,
                modifier =
                    Modifier.fillMaxSize()
                        .parallaxLayoutModifier(scrollState = scrollState, rate = 2),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                contentScale = ContentScale.Crop,
            )
        },
        content = content,
    )
}

@Composable
fun ItemHeader(
    item: FindroidItem,
    lazyListState: LazyListState,
    showLogo: Boolean = false,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    ItemHeaderBase(
        item = item,
        showLogo = showLogo,
        backdropImage = {
            FallbackAsyncImage(
                imageUris = item.images.backdropCandidates(),
                contentDescription = null,
                modifier =
                    Modifier.fillMaxSize()
                        .parallaxLayoutModifier(lazyListState = lazyListState, rate = 2),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                contentScale = ContentScale.Crop,
            )
        },
        content = content,
    )
}

@Composable
private fun ItemHeaderBase(
    item: FindroidItem,
    showLogo: Boolean = false,
    backdropImage: @Composable (() -> Unit),
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.height(288.dp).clipToBounds()) {
        backdropImage()
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.1f))
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, backgroundColor),
                        startY = 0f,
                    )
            )
        }
        content()
        if (showLogo) {
            FallbackAsyncImage(
                imageUris = item.images.logoCandidates(),
                contentDescription = null,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(MaterialTheme.spacings.default)
                        .height(100.dp)
                        .fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
