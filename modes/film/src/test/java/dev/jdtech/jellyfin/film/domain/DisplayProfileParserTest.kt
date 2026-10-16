package dev.jdtech.jellyfin.film.domain

import dev.jdtech.jellyfin.models.DisplayProfile
import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayProfileParserTest {
    @Test
    fun recognizesEveryJellyfin1010VideoRange() {
        val profiles =
            mapOf(
                VideoRangeType.SDR to DisplayProfile.SDR,
                VideoRangeType.HDR10 to DisplayProfile.HDR10,
                VideoRangeType.HDR10_PLUS to DisplayProfile.HDR10_PLUS,
                VideoRangeType.HLG to DisplayProfile.HLG,
                VideoRangeType.DOVI to DisplayProfile.DOLBY_VISION,
                VideoRangeType.DOVI_WITH_HDR10 to DisplayProfile.DOLBY_VISION,
                VideoRangeType.DOVI_WITH_HLG to DisplayProfile.DOLBY_VISION,
                VideoRangeType.DOVI_WITH_SDR to DisplayProfile.DOLBY_VISION,
            )

        for ((range, profile) in profiles) {
            assertEquals(range.serialName, profile, parseDisplayProfile(range, null))
        }
    }

    @Test
    fun recognizesDolbyVisionTitleWhenVideoRangeIsMissingOrUnknown() {
        for (range in listOf(null, VideoRangeType.UNKNOWN)) {
            assertEquals(
                DisplayProfile.DOLBY_VISION,
                parseDisplayProfile(range, "Dolby Vision Profile 8.1"),
            )
        }
    }

    @Test
    fun dolbyVisionTitleTakesPrecedenceOverBaseLayerRange() {
        assertEquals(
            DisplayProfile.DOLBY_VISION,
            parseDisplayProfile(VideoRangeType.HDR10, "Dolby Vision Profile 8.1"),
        )
    }

    @Test
    fun ignoresMissingAndBlankDolbyVisionTitles() {
        for (title in listOf(null, "", " \t\n")) {
            assertEquals(DisplayProfile.HDR10, parseDisplayProfile(VideoRangeType.HDR10, title))
            assertNull(parseDisplayProfile(VideoRangeType.UNKNOWN, title))
            assertNull(parseDisplayProfile(null, title))
        }
    }
}
