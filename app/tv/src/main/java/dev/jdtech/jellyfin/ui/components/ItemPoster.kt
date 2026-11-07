package dev.jdtech.jellyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.core.presentation.components.FallbackAsyncImage
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie

enum class Direction {
    HORIZONTAL,
    VERTICAL,
}

@Composable
fun ItemPoster(item: FindroidItem, direction: Direction, modifier: Modifier = Modifier) {
    val imageUris =
        when {
            direction == Direction.HORIZONTAL && item is FindroidMovie ->
                item.images.backdropCandidates()
            else ->
                item.images.primaryCandidates(
                    preferShow = direction == Direction.VERTICAL && item is FindroidEpisode
                )
        }

    FallbackAsyncImage(
        imageUris = imageUris,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(if (direction == Direction.HORIZONTAL) 1.77f else 0.66f)
                .background(MaterialTheme.colorScheme.surface),
    )
}
