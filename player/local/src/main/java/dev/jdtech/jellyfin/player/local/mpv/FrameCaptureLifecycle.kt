package dev.jdtech.jellyfin.player.local.mpv

/**
 * Defer native destruction during a screenshot without blocking the UI thread on image encoding.
 */
internal class FrameCaptureLifecycle(private val destroy: () -> Unit) {
    private val lock = Any()
    private var released = false
    private var capturing = false

    val isReleased: Boolean
        get() = synchronized(lock) { released }

    fun capture(action: () -> Unit) {
        synchronized(lock) {
            check(!released) { "The player has been released" }
            check(!capturing) { "A video frame capture is already in progress" }
            capturing = true
        }
        try {
            action()
        } finally {
            val destroyNow =
                synchronized(lock) {
                    capturing = false
                    released
                }
            if (destroyNow) destroy()
        }
    }

    fun release() {
        val destroyNow =
            synchronized(lock) {
                if (released) return
                released = true
                !capturing
            }
        if (destroyNow) destroy()
    }
}
