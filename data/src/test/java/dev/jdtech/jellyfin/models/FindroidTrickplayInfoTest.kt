package dev.jdtech.jellyfin.models

import org.jellyfin.sdk.model.api.TrickplayInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class FindroidTrickplayInfoTest {
    @Test
    fun convertsJellyfin1010TrickplayMetadataWithoutLosingFields() {
        val info =
            TrickplayInfo(
                    width = 320,
                    height = 180,
                    tileWidth = 10,
                    tileHeight = 8,
                    thumbnailCount = 120,
                    interval = 10000,
                    bandwidth = 20000,
                )
                .toFindroidTrickplayInfo()

        assertEquals(
            FindroidTrickplayInfo(
                width = 320,
                height = 180,
                tileWidth = 10,
                tileHeight = 8,
                thumbnailCount = 120,
                interval = 10000,
                bandwidth = 20000,
            ),
            info,
        )
        assertEquals(info, info.toFindroidTrickplayInfoDto("source").toFindroidTrickplayInfo())
    }
}
