package my.github.MrxSiN.pixellauncherevolved.lock

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFlightScreenLockerTest {

    @Test
    fun `one gesture reaches the screen once`() {
        val calls = AtomicInteger()
        val locker = SingleFlightScreenLocker { calls.incrementAndGet(); true }

        assertTrue(locker.lock())

        assertEquals(1, calls.get())
    }

    @Test
    fun `a second gesture taken while the first is still running is dropped`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()

        val locker = SingleFlightScreenLocker {
            calls.incrementAndGet()
            started.countDown()
            release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            true
        }

        val first = Thread { locker.lock() }.apply { start() }
        assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        // Reported as done: the screen is going off, on the call in flight.
        assertTrue(locker.lock())
        assertEquals(1, calls.get())

        release.countDown()
        first.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))
    }

    @Test
    fun `the next gesture is taken once the last one is done`() {
        val calls = AtomicInteger()
        val locker = SingleFlightScreenLocker { calls.incrementAndGet(); true }

        locker.lock()
        locker.lock()

        assertEquals(2, calls.get())
    }

    @Test
    fun `a failure is reported and does not keep later gestures out`() {
        val calls = AtomicInteger()
        val locker = SingleFlightScreenLocker { calls.incrementAndGet() > 1 }

        assertFalse(locker.lock())
        assertTrue(locker.lock())

        assertEquals(2, calls.get())
    }

    @Test
    fun `a thrown failure does not keep later gestures out`() {
        val calls = AtomicInteger()
        val locker = SingleFlightScreenLocker {
            if (calls.incrementAndGet() == 1) throw IllegalStateException("no shell")
            true
        }

        runCatching { locker.lock() }

        assertTrue(locker.lock())
        assertEquals(2, calls.get())
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
    }
}
