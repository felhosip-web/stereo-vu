package com.example.stereovu

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying lifecycle state transitions, thread synchronization,
 * bounded termination, stop/release serialization, MediaProjection ownership,
 * and exception handling for AudioCaptureSession.
 */
class AudioRecordLifecycleStateTest {

    private class MockAudioRecord(
        val blockInReadLatch: CountDownLatch? = null,
        val unblockReadLatch: CountDownLatch? = null,
        val blockInStopLatch: CountDownLatch? = null,
        val unblockStopLatch: CountDownLatch? = null,
        val onReleaseCallback: (() -> Unit)? = null
    ) : IAudioRecord {
        val startRecordingCallCount = AtomicInteger(0)
        val stopCallCount = AtomicInteger(0)
        val releaseCallCount = AtomicInteger(0)
        val readCallCount = AtomicInteger(0)
        val isReading = AtomicBoolean(false)
        val isStopping = AtomicBoolean(false)
        val releasedWhileReading = AtomicBoolean(false)
        val releasedWhileStopping = AtomicBoolean(false)

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
            isStopping.set(true)
            try {
                stopCallCount.incrementAndGet()
                blockInStopLatch?.countDown()
                unblockStopLatch?.await(2, TimeUnit.SECONDS)
                recordingState = 1 // RECORDSTATE_STOPPED
                unblockReadLatch?.countDown()
            } finally {
                isStopping.set(false)
            }
        }

        override fun release() {
            releaseCallCount.incrementAndGet()
            if (isReading.get()) {
                releasedWhileReading.set(true)
            }
            if (isStopping.get()) {
                releasedWhileStopping.set(true)
            }
            state = 0 // STATE_UNINITIALIZED
            onReleaseCallback?.invoke()
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
    fun testStopAndReleaseAreSerializedAndNeverConcurrent() {
        val blockInStopLatch = CountDownLatch(1)
        val unblockStopLatch = CountDownLatch(1)
        val mockRecord = MockAudioRecord(
            blockInStopLatch = blockInStopLatch,
            unblockStopLatch = unblockStopLatch
        )

        val session = AudioCaptureSession(mockRecord) { _, isRunning ->
            while (isRunning()) {
                Thread.sleep(5)
            }
        }

        session.start("TestWorkerStopSerial")
        Thread.sleep(20)

        // Asynchronously invoke stopAndAwait on thread A; it will enter stop() and block on unblockStopLatch
        val stopThread = Thread {
            session.stopAndAwait(1000)
        }
        stopThread.start()

        // Wait until thread A is actively inside stop()
        assertTrue(blockInStopLatch.await(1, TimeUnit.SECONDS))
        assertTrue("record.stop() must be actively executing", mockRecord.isStopping.get())

        // At this moment, release must NOT have executed
        assertEquals("release must not execute concurrently with stop", 0, mockRecord.releaseCallCount.get())

        // Unblock stop()
        unblockStopLatch.countDown()
        stopThread.join(1000)

        // Now stop has completed and worker has exited
        assertEquals(1, mockRecord.releaseCallCount.get())
        assertFalse("release must never execute while stop() was active", mockRecord.releasedWhileStopping.get())
    }

    @Test
    fun testWorkerExceptionIsCaughtReportedAndCleansUp() {
        val mockRecord = MockAudioRecord()
        val reportedError = AtomicBoolean(false)
        val unexpectedExitSignaled = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val session = AudioCaptureSession(
            record = mockRecord,
            onError = {
                reportedError.set(true)
            },
            onUnexpectedTermination = {
                unexpectedExitSignaled.set(true)
                latch.countDown()
            }
        ) { _, _ ->
            throw IllegalStateException("Simulated hardware failure in capture loop")
        }

        session.start("TestWorkerException")
        assertTrue(latch.await(1, TimeUnit.SECONDS))

        assertTrue("Exception must be reported to onError callback", reportedError.get())
        assertTrue("Unexpected termination callback must be triggered", unexpectedExitSignaled.get())
        assertEquals("Release must run exactly once even on unhandled worker exception", 1, mockRecord.releaseCallCount.get())
        assertFalse("Session must no longer be active", session.isActive)
    }

    @Test
    fun testIntentionalShutdownDoesNotTriggerUnexpectedTermination() {
        val mockRecord = MockAudioRecord()
        val unexpectedExitSignaled = AtomicBoolean(false)

        val session = AudioCaptureSession(
            record = mockRecord,
            onUnexpectedTermination = {
                unexpectedExitSignaled.set(true)
            }
        ) { _, isRunning ->
            while (isRunning()) {
                Thread.sleep(5)
            }
        }

        session.start("TestWorkerIntentional")
        Thread.sleep(20)

        assertTrue(session.stopAndAwait(1000))
        assertFalse("Intentional shutdown must NOT trigger onUnexpectedTermination", unexpectedExitSignaled.get())
        assertEquals(1, mockRecord.releaseCallCount.get())
    }

    @Test
    fun testPerSessionResourceOwnershipDoesNotAffectNewerSession() {
        // Simulates two consecutive sessions, ensuring old session cleanup cannot stop newer session resources
        val oldResourceStopCount = AtomicInteger(0)
        val newResourceStopCount = AtomicInteger(0)

        val oldRecord = MockAudioRecord(onReleaseCallback = {
            oldResourceStopCount.incrementAndGet()
        })
        val newRecord = MockAudioRecord(onReleaseCallback = {
            newResourceStopCount.incrementAndGet()
        })

        val oldSession = AudioCaptureSession(oldRecord) { _, isRunning ->
            while (isRunning()) {
                Thread.sleep(10)
            }
        }
        val newSession = AudioCaptureSession(newRecord) { _, isRunning ->
            while (isRunning()) {
                Thread.sleep(10)
            }
        }

        oldSession.start("OldSession")
        oldSession.stopAndAwait(500)
        assertEquals(1, oldResourceStopCount.get())
        assertEquals(0, newResourceStopCount.get())

        newSession.start("NewSession")
        // Triggering any stale cleanup on old session must not touch new session's resources
        oldSession.stopAndAwait(500)
        assertEquals(1, oldResourceStopCount.get())
        assertEquals(0, newResourceStopCount.get())

        newSession.stopAndAwait(500)
        assertEquals(1, newResourceStopCount.get())
    }

    @Test
    fun testCannotRestartAlreadyReleasedSession() {
        val mockRecord = MockAudioRecord()
        val session = AudioCaptureSession(mockRecord) { _, _ -> }

        assertTrue(session.start("TestInitialRun"))
        session.stopAndAwait(500)

        assertEquals("Session must be released after completion", 1, mockRecord.releaseCallCount.get())
        assertFalse("Session must not be active", session.isActive)

        // Attempting to restart an already released session must return false
        val restarted = session.start("TestRestartRun")
        assertFalse("Starting an already released session must return false", restarted)
        assertFalse("Session must remain inactive", session.isActive)

        // Must not execute any additional startRecording or release operations
        assertEquals("startRecording must not be called again", 1, mockRecord.startRecordingCallCount.get())
        assertEquals("release must not be called again", 1, mockRecord.releaseCallCount.get())
    }

    @Test
    fun testStaleSessionCallbackDoesNotStopNewerSession() {
        // Simulates the identity check pattern in FloatingVuService.onUnexpectedTermination
        val captureLock = Any()
        var currentSession: AudioCaptureSession? = null
        val serviceStopped = AtomicBoolean(false)
        val levelsReset = AtomicBoolean(false)

        val record1 = MockAudioRecord()
        val record2 = MockAudioRecord()

        val session1 = AudioCaptureSession(record1) { _, _ -> }
        val session2 = AudioCaptureSession(record2) { _, _ -> }

        synchronized(captureLock) {
            currentSession = session2
        }

        // Simulate session1 (an older/replaced session) firing its unexpected termination callback
        fun onSessionTerminated(session: AudioCaptureSession) {
            val isCurrent = synchronized(captureLock) {
                if (currentSession === session) {
                    currentSession = null
                    true
                } else {
                    false
                }
            }
            if (isCurrent) {
                levelsReset.set(true)
                serviceStopped.set(true)
            }
        }

        onSessionTerminated(session1)

        // Because session1 !== session2, active session2 must be preserved and no shutdown triggered
        assertFalse("Service must NOT be stopped by obsolete session", serviceStopped.get())
        assertFalse("Levels must NOT be reset by obsolete session", levelsReset.get())
        assertSame("Active session must remain untouched", session2, currentSession)

        // When the current active session2 terminates, it must properly trigger
        onSessionTerminated(session2)
        assertTrue("Service must be stopped by current session", serviceStopped.get())
        assertTrue("Levels must be reset by current session", levelsReset.get())
        assertNull("Current session reference must be cleared", currentSession)
    }

    @Test
    fun testInitializationFailureStopsProjectionEvenIfReleaseThrows() {
        val projectionStopped = AtomicBoolean(false)
        val releaseAttempted = AtomicBoolean(false)

        val failingRecord = object : IAudioRecord {
            override val state: Int = 0 // STATE_UNINITIALIZED
            override val recordingState: Int = 1
            override fun startRecording() {}
            override fun read(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int = 0
            override fun stop() {}
            override fun release() {
                releaseAttempted.set(true)
                throw RuntimeException("Simulated native release failure")
            }
        }

        // Simulate the initialization check pattern from FloatingVuService
        if (failingRecord.state != 1 /* STATE_INITIALIZED */) {
            try {
                failingRecord.release()
            } catch (t: Throwable) {
                // Expected to catch release error
            } finally {
                projectionStopped.set(true)
            }
        }

        assertTrue("Release must be attempted", releaseAttempted.get())
        assertTrue("Projection must be stopped despite release throwing an exception", projectionStopped.get())
    }

    @Test
    fun testJoinTimeoutDoesNotCausePrematureRelease() {
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

        session.start("TestWorkerTimeout")
        assertTrue(enterReadLatch.await(1, TimeUnit.SECONDS))

        val joinedInTime = session.stopAndAwait(50)
        assertFalse("Join should have timed out", joinedInTime)
        assertEquals("Recorder must NOT be released while worker thread is still alive", 0, mockRecord.releaseCallCount.get())

        releaseWorkerLatch.countDown()
        Thread.sleep(200)

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

        val joined = session.stopAndAwait(50)
        assertFalse(joined)
        assertEquals(0, mockRecord.releaseCallCount.get())

        releaseWorkerLatch.countDown()
        Thread.sleep(200)

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
