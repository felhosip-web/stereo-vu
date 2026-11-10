package com.example.stereovu

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying lifecycle state transitions, thread synchronization,
 * bounded termination, and single release ownership for AudioRecord.
 */
class AudioRecordLifecycleStateTest {

    private class MockAudioRecord(
        val blockInReadLatch: CountDownLatch? = null,
        val unblockReadLatch: CountDownLatch? = null
    ) : IAudioRecord {
        val startRecordingCallCount = AtomicInteger(0)
        val stopCallCount = AtomicInteger(0)
        val releaseCallCount = AtomicInteger(0)
        val readCallCount = AtomicInteger(0)
        val isReading = AtomicBoolean(false)
        val releasedWhileReading = AtomicBoolean(false)

        override var state: Int = 1 // STATE_INITIALIZED
        override var recordingState: Int = 1 // RECORDSTATE_STOPPED

        override fun startRecording() {
            startRecordingCallCount.incrementAndGet()
            recordingState = 3 // RECORDSTATE_RECORDING
        }

        override fun read(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
            readCallCount.incrementAndGet()
            isReading.set(true)
            try {
                blockInReadLatch?.countDown()
                unblockReadLatch?.await(2, TimeUnit.SECONDS)
                if (releaseCallCount.get() > 0) {
                    releasedWhileReading.set(true)
                }
                return audioData.size
            } finally {
                isReading.set(false)
            }
        }

        override fun stop() {
            stopCallCount.incrementAndGet()
            recordingState = 1 // RECORDSTATE_STOPPED
            // If reader is blocked waiting on unblockReadLatch, simulate unblocking on stop
            unblockReadLatch?.countDown()
        }

        override fun release() {
            releaseCallCount.incrementAndGet()
            if (isReading.get()) {
                releasedWhileReading.set(true)
            }
            state = 0 // STATE_UNINITIALIZED
        }
    }

    @Test
    fun testAudioRecordErrorCodeClassification() {
        val ERROR = -1
        val ERROR_BAD_VALUE = -2
        val ERROR_INVALID_OPERATION = -3
        val ERROR_DEAD_OBJECT = -6

        fun isTerminalError(code: Int): Boolean {
            return code < 0
        }

        assertTrue(isTerminalError(ERROR))
        assertTrue(isTerminalError(ERROR_BAD_VALUE))
        assertTrue(isTerminalError(ERROR_INVALID_OPERATION))
        assertTrue(isTerminalError(ERROR_DEAD_OBJECT))
        assertFalse(isTerminalError(0))
        assertFalse(isTerminalError(1024))
    }

    @Test
    fun testReleaseHappensExactlyOnceOnNormalShutdown() {
        val mockRecord = MockAudioRecord()
        val session = AudioCaptureSession(mockRecord) { record, isRunning ->
            val buf = ShortArray(128)
            while (isRunning()) {
                record.read(buf, 0, buf.size)
                Thread.sleep(10)
            }
        }

        assertTrue(session.start("TestWorkerNormal"))
        assertTrue(session.isActive)
        Thread.sleep(50)

        assertTrue(session.stopAndAwait(1000))
        assertFalse(session.isActive)

        assertEquals("release must be called exactly once", 1, mockRecord.releaseCallCount.get())
        assertFalse("must never release while read is executing", mockRecord.releasedWhileReading.get())
    }

    @Test
    fun testReleaseCannotHappenWhileWorkerIsReading() {
        val enterReadLatch = CountDownLatch(1)
        val leaveReadLatch = CountDownLatch(1)
        val mockRecord = MockAudioRecord(blockInReadLatch = enterReadLatch, unblockReadLatch = leaveReadLatch)

        val session = AudioCaptureSession(mockRecord) { record, isRunning ->
            val buf = ShortArray(128)
            while (isRunning()) {
                record.read(buf, 0, buf.size)
                break
            }
        }

        session.start("TestWorkerReading")
        assertTrue(enterReadLatch.await(1, TimeUnit.SECONDS))
        assertTrue("Recorder must be reading", mockRecord.isReading.get())

        // At this point worker is inside read(); release must NOT have occurred
        assertEquals(0, mockRecord.releaseCallCount.get())

        // Stop session, which calls record.stop() and unblocks read
        session.stopAndAwait(1000)
        assertFalse("Recorder must no longer be reading", mockRecord.isReading.get())
        assertEquals("Release must have occurred exactly once after reader finished", 1, mockRecord.releaseCallCount.get())
        assertFalse("Must never release while worker was reading", mockRecord.releasedWhileReading.get())
    }

    @Test
    fun testJoinTimeoutDoesNotCausePrematureRelease() {
        val enterReadLatch = CountDownLatch(1)
        val releaseWorkerLatch = CountDownLatch(1)
        val mockRecord = MockAudioRecord()

        val session = AudioCaptureSession(mockRecord) { _, _ ->
            enterReadLatch.countDown()
            // Ignore interruption to simulate an uninterruptible blocking operation in HAL driver
            while (releaseWorkerLatch.count > 0) {
                try {
                    releaseWorkerLatch.await(1, TimeUnit.SECONDS)
                } catch (ie: InterruptedException) {
                    // Suppress interruption so thread remains alive past timeout
                }
            }
        }

        session.start("TestWorkerTimeout")
        assertTrue(enterReadLatch.await(1, TimeUnit.SECONDS))

        // stopAndAwait with a short timeout (50ms) to force a join timeout
        val joinedInTime = session.stopAndAwait(50)
        assertFalse("Join should have timed out", joinedInTime)

        // Bounded join timed out, but worker is still alive; recorder MUST NOT be released prematurely!
        assertEquals("Recorder must NOT be released while worker thread is still alive", 0, mockRecord.releaseCallCount.get())

        // Now allow worker to exit
        releaseWorkerLatch.countDown()
        Thread.sleep(200)

        // Once worker finishes its loop and enters finally, release must be executed exactly once
        assertEquals("Recorder must be released once worker completes", 1, mockRecord.releaseCallCount.get())
    }

    @Test
    fun testWorkerCleanupAfterTimeoutIsSafeAndNoDoubleRelease() {
        val enterReadLatch = CountDownLatch(1)
        val releaseWorkerLatch = CountDownLatch(1)
        val mockRecord = MockAudioRecord()

        val session = AudioCaptureSession(mockRecord) { _, _ ->
            enterReadLatch.countDown()
            while (releaseWorkerLatch.count > 0) {
                try {
                    releaseWorkerLatch.await(1, TimeUnit.SECONDS)
                } catch (ie: InterruptedException) {
                    // Suppress interruption
                }
            }
        }

        session.start("TestWorkerCleanup")
        assertTrue(enterReadLatch.await(1, TimeUnit.SECONDS))

        // Timeout join
        val joined = session.stopAndAwait(50)
        assertFalse(joined)
        assertEquals(0, mockRecord.releaseCallCount.get())

        // Allow worker to finish
        releaseWorkerLatch.countDown()
        Thread.sleep(200)

        // Repeated stop calls after worker has terminated
        session.stopAndAwait(100)
        session.stopAndAwait(100)

        assertEquals("Release must be idempotent and called exactly once", 1, mockRecord.releaseCallCount.get())
    }

    @Test
    fun testConcurrentStopCallsAreIdempotent() {
        val mockRecord = MockAudioRecord()
        val session = AudioCaptureSession(mockRecord) { record, isRunning ->
            val buf = ShortArray(64)
            while (isRunning()) {
                record.read(buf, 0, buf.size)
                Thread.sleep(5)
            }
        }

        session.start("TestConcurrentStop")
        Thread.sleep(30)

        val latch = CountDownLatch(1)
        val stopThreads = List(8) {
            Thread {
                latch.await()
                session.stopAndAwait(1000)
            }
        }
        stopThreads.forEach { it.start() }
        latch.countDown()
        stopThreads.forEach { it.join() }

        assertEquals("Release must be called exactly once despite 8 concurrent stop calls", 1, mockRecord.releaseCallCount.get())
    }
}
