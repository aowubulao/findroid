package dev.jdtech.jellyfin.player.local.mpv

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCaptureLifecycleTest {
    @Test
    fun destroysImmediatelyWhenNoCaptureIsRunning() {
        val destroyed = AtomicInteger()
        val lifecycle = FrameCaptureLifecycle { destroyed.incrementAndGet() }
        lifecycle.release()
        lifecycle.release()
        assertEquals(1, destroyed.get())
        assertThrows(IllegalStateException::class.java) { lifecycle.capture {} }
    }

    @Test
    fun releaseDoesNotWaitForNativeCaptureAndDestroysExactlyOnceAfterward() {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val destroyed = AtomicInteger()
        val lifecycle = FrameCaptureLifecycle { destroyed.incrementAndGet() }
        val worker = thread {
            lifecycle.capture {
                started.countDown()
                assertTrue(finish.await(5, TimeUnit.SECONDS))
            }
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            lifecycle.release()
            lifecycle.release()
            assertTrue(lifecycle.isReleased)
            assertEquals(0, destroyed.get())
            assertThrows(IllegalStateException::class.java) { lifecycle.capture {} }
        } finally {
            finish.countDown()
            worker.join(5000)
        }
        assertFalse(worker.isAlive)
        assertEquals(1, destroyed.get())
    }

    @Test
    fun failedCaptureReleasesItsReservation() {
        val destroyed = AtomicInteger()
        val lifecycle = FrameCaptureLifecycle { destroyed.incrementAndGet() }
        assertThrows(IllegalArgumentException::class.java) {
            lifecycle.capture { throw IllegalArgumentException("capture failed") }
        }
        lifecycle.capture {}
        lifecycle.release()
        assertEquals(1, destroyed.get())
    }

    @Test
    fun rejectsOverlappingCaptures() {
        val lifecycle = FrameCaptureLifecycle {}
        lifecycle.capture {
            assertThrows(IllegalStateException::class.java) { lifecycle.capture {} }
        }
    }
}
