package dev.jdtech.jellyfin.api

import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.Assert.assertEquals
import org.junit.Test

class JellyfinCompatibilityTest {
    @Test
    fun sdkKeepsJellyfin1010Supported() {
        assertEquals(ServerVersion(10, 10, 0, 0), Jellyfin.minimumVersion)
        assertEquals(ServerVersion(10, 10, 7), Jellyfin.apiVersion)
    }

    @Test
    fun readsJellyfin1010TrickplayMetadata() {
        val item =
            ApiSerializer.decodeResponseBody<BaseItemDto>(
                """
                {
                    "Id": "00000000000000000000000000000001",
                    "Type": "Movie",
                    "Trickplay": {
                        "00000000000000000000000000000002": {
                            "320": {
                                "Width": 320,
                                "Height": 180,
                                "TileWidth": 10,
                                "TileHeight": 10,
                                "ThumbnailCount": 120,
                                "Interval": 10000,
                                "Bandwidth": 20000
                            }
                        }
                    }
                }
                """
                    .trimIndent()
            )

        val info = item.trickplay!!.values.single()["320"]!!
        assertEquals(320, info.width)
        assertEquals(180, info.height)
        assertEquals(120, info.thumbnailCount)
        assertEquals(10000, info.interval)
    }

    @Test
    fun readsJellyfin1010DolbyVisionMetadata() {
        val stream =
            ApiSerializer.decodeResponseBody<MediaStream>(
                """
                {
                    "Type": "Video",
                    "Index": 0,
                    "IsInterlaced": false,
                    "IsDefault": true,
                    "IsForced": false,
                    "IsHearingImpaired": false,
                    "IsExternal": false,
                    "IsTextSubtitleStream": false,
                    "SupportsExternalStream": false,
                    "VideoRangeType": "DOVIWithHDR10",
                    "VideoDoViTitle": "Dolby Vision Profile 8.1"
                }
                """
                    .trimIndent()
            )

        assertEquals(MediaStreamType.VIDEO, stream.type)
        assertEquals(VideoRangeType.DOVI_WITH_HDR10, stream.videoRangeType)
        assertEquals("Dolby Vision Profile 8.1", stream.videoDoViTitle)
    }

    @Test
    fun preservesDolbyVisionTitleWhenRangeIsUnknownToTheSdk() {
        val stream =
            ApiSerializer.decodeResponseBody<MediaStream>(
                """
                {
                    "Type": "Video",
                    "Index": 0,
                    "IsInterlaced": false,
                    "IsDefault": true,
                    "IsForced": false,
                    "IsHearingImpaired": false,
                    "IsExternal": false,
                    "IsTextSubtitleStream": false,
                    "SupportsExternalStream": false,
                    "VideoRangeType": "DOVIWithHDR10Plus",
                    "VideoDoViTitle": "Dolby Vision Profile 8.1",
                    "AdditionalServerField": true
                }
                """
                    .trimIndent()
            )

        assertEquals(VideoRangeType.UNKNOWN, stream.videoRangeType)
        assertEquals("Dolby Vision Profile 8.1", stream.videoDoViTitle)
    }
}
