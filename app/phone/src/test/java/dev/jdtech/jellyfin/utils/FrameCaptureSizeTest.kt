package dev.jdtech.jellyfin.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameCaptureSizeTest {
    @Test
    fun keepsRenderedResolutionWithoutUpscaling() {
        assertEquals(1920 to 1080, frameCaptureSize(1920, 1080))
        assertEquals(720 to 1280, frameCaptureSize(720, 1280))
        assertEquals(3840 to 2160, frameCaptureSize(3840, 2160))
    }

    @Test
    fun boundsLargeAndZoomedSurfacesWithoutChangingAspectRatio() {
        assertEquals(4096 to 2304, frameCaptureSize(7680, 4320))
        assertEquals(2304 to 4096, frameCaptureSize(4320, 7680))
        assertEquals(4096 to 4096, frameCaptureSize(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(4096 to 1, frameCaptureSize(Int.MAX_VALUE, 1))
    }

    @Test
    fun capturesFourByThreeVideoWithoutSixteenByNineScreenPadding() {
        // On a 1920x1080 screen, PlayerView FIT measures the video surface at 1440x1080.
        assertEquals(1440 to 1080, frameCaptureSize(1440, 1080))
        // ZOOM expands the complete 4:3 surface to 1920x1440; only its parent clips the view.
        assertEquals(1920 to 1440, frameCaptureSize(1920, 1440))
        assertEquals(4096 to 3072, frameCaptureSize(8192, 6144))
    }

    @Test
    fun preservesPortraitAndUltrawideVideoAspectRatios() {
        assertEquals(1080 to 1440, frameCaptureSize(1080, 1440))
        assertEquals(2520 to 1080, frameCaptureSize(2520, 1080))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSurfaceBeforeLayout() {
        frameCaptureSize(0, 1080)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidHeight() {
        frameCaptureSize(1920, -1)
    }
}
