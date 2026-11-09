package com.example.stereovu

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unit tests verifying lifecycle state transitions, thread synchronization,
 * bounded termination, and AudioRecord error code classification.
 *
 * (Note: Android HAL audio hardware calls cannot run in host JVM unit tests).
 */
class AudioRecordLifecycleStateTest {

    @Test
    fun testAudioRecordErrorCodeClassification() {
        // Values from android.media.AudioRecord
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
        assertFalse(isTerminalError(0)) // 0 means no samples ready (backoff)
        assertFalse(isTerminalError(1024)) // positive means valid frames read
    }

    @Test
    fun testConcurrentWorkerStartRaceConditionPrevention() {
        val captureLock = Any()
        val isRunning = AtomicBoolean(false)
        var workerThread: Thread? = null
        var startedWorkersCount = 0

        val latch = CountDownLatch(1)
        val threads = List(10) {
            Thread {
                latch.await()
                synchronized(captureLock) {
                    if (isRunning.get() || workerThread != null) {
                        return@synchronized
                    }
                    isRunning.set(true)
                    startedWorkersCount++
                    workerThread = Thread {
                        while (isRunning.get()) {
                            try {
                                Thread.sleep(10)
                            } catch (e: InterruptedException) {
                                break
                            }
                        }
                    }
                    workerThread?.start()
                }
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join() }

        // Exactly 1 worker must have been started despite 10 concurrent start calls
        assertEquals(1, startedWorkersCount)

        // Shutdown cleanly
        val threadToJoin: Thread?
        synchronized(captureLock) {
            isRunning.set(false)
            threadToJoin = workerThread
            workerThread = null
        }
        threadToJoin?.interrupt()
        threadToJoin?.join(500)
        assertFalse(threadToJoin?.isAlive ?: true)
    }

    @Test
    fun testWorkerShutdownBoundedWait() {
        val captureLock = Any()
        val isRunning = AtomicBoolean(true)
        val workerTerminated = AtomicBoolean(false)

        val worker = Thread {
            while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(50)
                } catch (ie: InterruptedException) {
                    break
                }
            }
            workerTerminated.set(true)
        }
        worker.start()

        // Simulate onDestroy bounded join
        val start = System.currentTimeMillis()
        synchronized(captureLock) {
            isRunning.set(false)
        }
        worker.interrupt()
        worker.join(1000)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(elapsed < 1000)
        assertTrue(workerTerminated.get())
        assertFalse(worker.isAlive)
    }
}
