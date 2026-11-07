package dev.jdtech.jellyfin.models

import android.net.Uri
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

data class FindroidImages(
    val primary: Uri? = null,
    val backdrop: Uri? = null,
    val logo: Uri? = null,
    val showPrimary: Uri? = null,
    // Inherited background/logo owners can be a season or series, as specified by Jellyfin.
    val showBackdrop: Uri? = null,
    val showLogo: Uri? = null,
    val parentPrimary: Uri? = null,
) {
    fun primaryCandidates(preferShow: Boolean = false): List<Uri> {
        val covers =
            if (preferShow) listOfNotNull(showPrimary, primary, parentPrimary)
            else listOfNotNull(primary, showPrimary, parentPrimary)
        return (covers + listOfNotNull(backdrop, showBackdrop)).distinct()
    }

    fun backdropCandidates(): List<Uri> =
        listOfNotNull(backdrop, showBackdrop, primary, showPrimary, parentPrimary).distinct()

    fun logoCandidates(): List<Uri> = listOfNotNull(logo, showLogo).distinct()
}

fun BaseItemDto.toFindroidImages(jellyfinRepository: JellyfinRepository): FindroidImages {
    return toFindroidImages(jellyfinRepository.getBaseUrl())
}

internal fun BaseItemDto.toFindroidImages(baseUrl: String): FindroidImages {
    val baseUri = Uri.parse(baseUrl)

    fun imageUri(itemId: String?, type: ImageType, tag: String?): Uri? {
        if (itemId.isNullOrBlank() || tag.isNullOrBlank()) return null
        val index = if (type == ImageType.BACKDROP) "/0" else ""
        return baseUri
            .buildUpon()
            .appendEncodedPath("items/$itemId/Images/$type$index")
            .appendQueryParameter("tag", tag)
            .build()
    }

    return FindroidImages(
        primary = imageUri(id.toString(), ImageType.PRIMARY, imageTags?.get(ImageType.PRIMARY)),
        backdrop = imageUri(id.toString(), ImageType.BACKDROP, backdropImageTags?.firstOrNull()),
        logo = imageUri(id.toString(), ImageType.LOGO, imageTags?.get(ImageType.LOGO)),
        showPrimary = imageUri(seriesId?.toString(), ImageType.PRIMARY, seriesPrimaryImageTag),
        // Jellyfin supplies the owner and tag for inherited images independently of the cover.
        showBackdrop =
            imageUri(
                parentBackdropItemId?.toString(),
                ImageType.BACKDROP,
                parentBackdropImageTags?.firstOrNull(),
            ),
        showLogo = imageUri(parentLogoItemId?.toString(), ImageType.LOGO, parentLogoImageTag),
        parentPrimary =
            imageUri(parentPrimaryImageItemId, ImageType.PRIMARY, parentPrimaryImageTag),
    )
}

fun FindroidMovieDto.toLocalFindroidImages(itemId: UUID): FindroidImages {
    return FindroidImages(
        primary = Uri.Builder().appendEncodedPath("images/$itemId/primary").build(),
        backdrop = Uri.Builder().appendEncodedPath("images/$itemId/backdrop").build(),
    )
}

fun FindroidShowDto.toLocalFindroidImages(itemId: UUID): FindroidImages {
    return FindroidImages(
        primary = Uri.Builder().appendEncodedPath("images/$itemId/primary").build(),
        backdrop = Uri.Builder().appendEncodedPath("images/$itemId/backdrop").build(),
    )
}

fun FindroidSeasonDto.toLocalFindroidImages(itemId: UUID): FindroidImages {
    return FindroidImages(
        primary = Uri.Builder().appendEncodedPath("images/$itemId/primary").build(),
        backdrop = Uri.Builder().appendEncodedPath("images/$itemId/backdrop").build(),
        showPrimary = Uri.Builder().appendEncodedPath("images/$seriesId/primary").build(),
        showBackdrop = Uri.Builder().appendEncodedPath("images/$seriesId/backdrop").build(),
    )
}

fun FindroidEpisodeDto.toLocalFindroidImages(itemId: UUID): FindroidImages {
    return FindroidImages(
        primary = Uri.Builder().appendEncodedPath("images/$itemId/primary").build(),
        backdrop = Uri.Builder().appendEncodedPath("images/$itemId/backdrop").build(),
        showPrimary = Uri.Builder().appendEncodedPath("images/$seriesId/primary").build(),
        showBackdrop = Uri.Builder().appendEncodedPath("images/$seriesId/backdrop").build(),
        parentPrimary = Uri.Builder().appendEncodedPath("images/$seasonId/primary").build(),
    )
}
