package dev.jdtech.jellyfin.utils

import kotlin.math.min
import kotlin.math.roundToInt

/** Bound the PixelCopy allocation while preserving the rendered video's aspect ratio. */
internal fun frameCaptureSize(width: Int, height: Int): Pair<Int, Int> {
    require(width > 0 && height > 0) { "The video surface has no size" }
    val scale = min(1.0, 4096.0 / maxOf(width, height))
    return (width * scale).roundToInt().coerceAtLeast(1) to
        (height * scale).roundToInt().coerceAtLeast(1)
}
