package dev.jdtech.jellyfin.models

import android.net.Uri
import java.util.UUID
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [29])
class FindroidImagesTest {
    private val itemId = UUID(0, 1)
    private val seriesId = UUID(0, 2)
    private val parentId = UUID(0, 3)
    private val baseUrl = "https://jellyfin.example/jellyfin/"

    @Test
    fun episodeWithoutItsOwnCoverUsesTheSeriesCover() {
        val images =
            BaseItemDto(
                    id = itemId,
                    type = BaseItemKind.EPISODE,
                    seriesId = seriesId,
                    seriesPrimaryImageTag = "series-cover",
                )
                .toFindroidImages(baseUrl)

        assertNull(images.primary)
        assertEquals(
            "/jellyfin/items/$seriesId/Images/Primary",
            images.primaryCandidates().first().path,
        )
        assertEquals("series-cover", images.primaryCandidates().first().getQueryParameter("tag"))
        assertEquals(images.primaryCandidates(), images.backdropCandidates())
        // A series cover must not advertise an unrelated backdrop or logo.
        assertNull(images.showBackdrop)
        assertNull(images.showLogo)
    }

    @Test
    fun readsInheritedBackgroundAndLogoWithoutASeriesCover() {
        val item =
            ApiSerializer.decodeResponseBody<BaseItemDto>(
                """
                {
                    "Id": "00000000000000000000000000000001",
                    "Type": "Season",
                    "SeriesId": "00000000000000000000000000000002",
                    "ParentBackdropItemId": "00000000000000000000000000000003",
                    "ParentBackdropImageTags": ["parent-background", "second-background"],
                    "ParentLogoItemId": "00000000000000000000000000000003",
                    "ParentLogoImageTag": "parent-logo"
                }
                """
                    .trimIndent()
            )
        val images = item.toFindroidImages(baseUrl)

        assertNull(images.showPrimary)
        assertEquals("/jellyfin/items/$parentId/Images/Backdrop/0", images.showBackdrop!!.path)
        assertEquals("parent-background", images.showBackdrop.getQueryParameter("tag"))
        assertEquals(images.showBackdrop, images.backdropCandidates().first())
        assertEquals("/jellyfin/items/$parentId/Images/Logo", images.showLogo!!.path)
        assertEquals("parent-logo", images.showLogo.getQueryParameter("tag"))
    }

    @Test
    fun episodeCoverRemainsFirstForCardsWhileDetailsUseTheInheritedBackground() {
        val images =
            BaseItemDto(
                    id = itemId,
                    type = BaseItemKind.EPISODE,
                    imageTags = mapOf(ImageType.PRIMARY to "episode-cover"),
                    seriesId = seriesId,
                    seriesPrimaryImageTag = "series-cover",
                    parentBackdropItemId = parentId,
                    parentBackdropImageTags = listOf("parent-background"),
                )
                .toFindroidImages(baseUrl)

        assertEquals(images.primary, images.primaryCandidates().first())
        assertEquals(images.showBackdrop, images.backdropCandidates().first())
        assertEquals(images.showPrimary, images.primaryCandidates(preferShow = true).first())
        assertEquals(images.primary, images.backdropCandidates()[1])
    }

    @Test
    fun ownBackgroundTakesPrecedenceAndCoversRemainAvailableAfterLoadFailure() {
        val images =
            BaseItemDto(
                    id = itemId,
                    type = BaseItemKind.MOVIE,
                    imageTags = mapOf(ImageType.PRIMARY to "cover"),
                    backdropImageTags = listOf("own-background"),
                    parentBackdropItemId = parentId,
                    parentBackdropImageTags = listOf("parent-background"),
                )
                .toFindroidImages(baseUrl)

        assertEquals(
            listOf(images.backdrop, images.showBackdrop, images.primary),
            images.backdropCandidates(),
        )
        assertEquals("own-background", images.backdrop!!.getQueryParameter("tag"))
    }

    @Test
    fun seriesWithoutABackgroundUsesItsPosterInDetails() {
        val images =
            BaseItemDto(
                    id = seriesId,
                    type = BaseItemKind.SERIES,
                    imageTags = mapOf(ImageType.PRIMARY to "series-cover"),
                )
                .toFindroidImages(baseUrl)

        assertNull(images.backdrop)
        assertEquals(listOf(images.primary), images.backdropCandidates())
    }

    @Test
    fun parentCoverIsAvailableWhenEpisodeAndSeriesCoversAreMissing() {
        val images =
            BaseItemDto(
                    id = itemId,
                    type = BaseItemKind.EPISODE,
                    parentPrimaryImageItemId = parentId.toString(),
                    parentPrimaryImageTag = "parent-cover",
                )
                .toFindroidImages(baseUrl)

        assertEquals(
            "/jellyfin/items/$parentId/Images/Primary",
            images.primaryCandidates().single().path,
        )
        assertEquals("parent-cover", images.parentPrimary!!.getQueryParameter("tag"))
    }

    @Test
    fun missingOwnersDoNotProduceUrlsContainingNull() {
        val images =
            BaseItemDto(
                    id = itemId,
                    type = BaseItemKind.SEASON,
                    seriesPrimaryImageTag = "cover-without-owner",
                    parentBackdropImageTags = listOf("background-without-owner"),
                    parentLogoImageTag = "logo-without-owner",
                    parentPrimaryImageTag = "parent-cover-without-owner",
                )
                .toFindroidImages(baseUrl)

        assertTrue(images.primaryCandidates().isEmpty())
        assertTrue(images.backdropCandidates().isEmpty())
        assertTrue(images.logoCandidates().isEmpty())
    }

    @Test
    fun blankTagsAndEmptyBackgroundListsAreTreatedAsMissingImages() {
        val images =
            BaseItemDto(
                    id = itemId,
                    type = BaseItemKind.SERIES,
                    imageTags = mapOf(ImageType.PRIMARY to "", ImageType.LOGO to " "),
                    backdropImageTags = emptyList(),
                )
                .toFindroidImages(baseUrl)

        assertTrue(images.primaryCandidates().isEmpty())
        assertTrue(images.backdropCandidates().isEmpty())
        assertTrue(images.logoCandidates().isEmpty())
    }

    @Test
    fun imageTagsAreEncodedWithoutChangingTheServerBasePath() {
        val tag = "cover&size=large /+"
        val images =
            BaseItemDto(
                    id = itemId,
                    type = BaseItemKind.MOVIE,
                    imageTags = mapOf(ImageType.PRIMARY to tag),
                )
                .toFindroidImages(baseUrl)

        assertEquals("/jellyfin/items/$itemId/Images/Primary", images.primary!!.path)
        assertEquals(tag, images.primary.getQueryParameter("tag"))
        assertNull(images.primary.getQueryParameter("size"))
    }

    @Test
    fun duplicateCandidatesAreOnlyRequestedOnce() {
        val image = Uri.parse("https://jellyfin.example/cover")
        val images = FindroidImages(primary = image, showPrimary = image, parentPrimary = image)

        assertEquals(listOf(image), images.primaryCandidates())
        assertEquals(listOf(image), images.backdropCandidates())
    }

    @Test
    fun offlineEpisodesCanUseTheDownloadedSeasonCover() {
        val images =
            FindroidEpisodeDto(
                    id = itemId,
                    serverId = null,
                    seasonId = parentId,
                    seriesId = seriesId,
                    name = "Episode",
                    seriesName = "Series",
                    overview = "",
                    indexNumber = 1,
                    indexNumberEnd = null,
                    parentIndexNumber = 1,
                    runtimeTicks = 0,
                    premiereDate = null,
                    communityRating = null,
                    chapters = null,
                )
                .toLocalFindroidImages(itemId)

        assertEquals("images/$parentId/primary", images.parentPrimary!!.path!!.trimStart('/'))
        assertTrue(images.primaryCandidates().contains(images.parentPrimary))
        assertTrue(images.backdropCandidates().contains(images.parentPrimary))
    }
}
