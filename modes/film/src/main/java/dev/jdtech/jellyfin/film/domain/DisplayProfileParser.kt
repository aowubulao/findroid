package dev.jdtech.jellyfin.film.domain

import dev.jdtech.jellyfin.models.DisplayProfile
import org.jellyfin.sdk.model.api.VideoRangeType

internal fun parseDisplayProfile(
    videoRangeType: VideoRangeType?,
    videoDoViTitle: String?,
): DisplayProfile? {
    // Jellyfin 10.10 can expose Dolby Vision through this title, even when the range is unknown.
    if (!videoDoViTitle.isNullOrBlank()) return DisplayProfile.DOLBY_VISION

    return when (videoRangeType) {
        VideoRangeType.SDR -> DisplayProfile.SDR
        VideoRangeType.HDR10 -> DisplayProfile.HDR10
        VideoRangeType.HDR10_PLUS -> DisplayProfile.HDR10_PLUS
        VideoRangeType.HLG -> DisplayProfile.HLG
        VideoRangeType.DOVI,
        VideoRangeType.DOVI_WITH_HLG,
        VideoRangeType.DOVI_WITH_SDR,
        VideoRangeType.DOVI_WITH_HDR10 -> DisplayProfile.DOLBY_VISION
        else -> null
    }
}
