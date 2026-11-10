package com.example.stereovu

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Abstraction over Android's [android.media.AudioRecord] allowing unit testing of
 * lifecycle state machines, thread ownership, and bounded shutdown.
 */
interface IAudioRecord {
    val state: Int
    val recordingState: Int
    fun startRecording()
    fun read(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int
    fun stop()
    fun release()
}

/**
 * Manages the lifecycle and ownership of an [IAudioRecord] capture session.
 *
 * Guarantees:
 * 1. Single release ownership: [IAudioRecord.release] is called exactly once per session.
 * 2. No premature release: If a worker thread is still running (or [stopAndAwait] times out),
 *    the service does NOT release the recorder concurrently. The worker's `finally` block
 *    is the authoritative release path once reader operations have ceased.
 * 3. Thread-safe idempotent shutdown.
 * 4. Safe restart: A new capture worker cannot start while an existing worker is still active.
 */
class AudioCaptureSession(
    private val record: IAudioRecord,
    private val loopBody: (record: IAudioRecord, isRunning: () -> Boolean) -> Unit
) {
    private val isRunning = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)
    private val sessionLock = Any()
    private var workerThread: Thread? = null

    val isActive: Boolean
        get() = isRunning.get() && (workerThread?.isAlive == true)

    /**
     * Start the capture session on a dedicated worker thread.
     * Returns true if started, false if already active.
     */
    fun start(threadName: String = "StereoVuCaptureWorker"): Boolean {
        synchronized(sessionLock) {
            if (isRunning.get() || (workerThread != null && workerThread!!.isAlive)) {
                return false
            }
            isRunning.set(true)
            val thread = Thread({
                try {
                    if (record.recordingState != 3 /* RECORDSTATE_RECORDING */) {
                        record.startRecording()
                    }
                    loopBody(record) { isRunning.get() && !Thread.currentThread().isInterrupted }
                } finally {
                    performRelease()
                    synchronized(sessionLock) {
                        isRunning.set(false)
                        if (workerThread === Thread.currentThread()) {
                            workerThread = null
                        }
                    }
                }
            }, threadName)
            workerThread = thread
            thread.start()
            return true
        }
    }

    /**
     * Signals the capture worker to stop, stops the audio recorder to unblock blocking reads,
     * and waits up to [timeoutMs] for the worker to exit.
     *
     * Note: If the join times out, this method does NOT call [IAudioRecord.release].
     * Release is strictly owned by the worker's exit path to prevent concurrent access.
     */
    fun stopAndAwait(timeoutMs: Long): Boolean {
        val threadToJoin: Thread?
        synchronized(sessionLock) {
            if (!isRunning.get() && workerThread == null) {
                // If the worker already exited before stopAndAwait, ensure release occurred
                performRelease()
                return true
            }
            isRunning.set(false)
            try {
                if (record.recordingState == 3 /* RECORDSTATE_RECORDING */) {
                    record.stop()
                }
            } catch (t: Throwable) {
                // Ignore exception during stop
            }
            threadToJoin = workerThread
        }

        if (threadToJoin != null && threadToJoin.isAlive) {
            try {
                threadToJoin.interrupt()
                threadToJoin.join(timeoutMs)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        val completed = threadToJoin == null || !threadToJoin.isAlive
        synchronized(sessionLock) {
            if (completed && workerThread === threadToJoin) {
                workerThread = null
            }
        }
        return completed
    }

    private fun performRelease() {
        if (isReleased.compareAndSet(false, true)) {
            try {
                if (record.recordingState == 3 /* RECORDSTATE_RECORDING */) {
                    record.stop()
                }
            } catch (t: Throwable) {
                // Ignore
            }
            try {
                record.release()
            } catch (t: Throwable) {
                // Ignore
            }
        }
    }
}
