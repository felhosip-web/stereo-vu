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
 * 2. Never release while reading: Worker reads without holding lock; release occurs strictly
 *    after read loop exits.
 * 3. Never release concurrently with stop: A dedicated `recordLifecycleLock` serializes `stop()`
 *    and `release()` calls.
 * 4. Bounded join timeout safety: A timeout in [stopAndAwait] does NOT authorize premature release.
 *    Release is strictly executed on the worker's finalization path.
 * 5. Worker exception safety: Any unexpected exception from `startRecording()` or `loopBody()`
 *    is caught and reported via [onError] without crashing the application.
 * 6. Unexpected worker termination: Invokes [onUnexpectedTermination] if the worker finishes
 *    without an intentional shutdown signal, allowing the UI to reset stale levels.
 */
class AudioCaptureSession(
    private val record: IAudioRecord,
    private val onError: ((Throwable) -> Unit)? = null,
    private val onUnexpectedTermination: (() -> Unit)? = null,
    private val loopBody: (record: IAudioRecord, isRunning: () -> Boolean) -> Unit
) {
    companion object {
        private const val TAG = "AudioCaptureSession"

        private fun logE(msg: String, t: Throwable? = null) {
            try {
                android.util.Log.e(TAG, msg, t)
            } catch (e: Throwable) {
                System.err.println("[$TAG] ERROR: $msg ${t?.message ?: ""}")
            }
        }

        private fun logW(msg: String, t: Throwable? = null) {
            try {
                android.util.Log.w(TAG, msg, t)
            } catch (e: Throwable) {
                System.err.println("[$TAG] WARN: $msg ${t?.message ?: ""}")
            }
        }
    }

    private val isRunning = AtomicBoolean(false)
    private val isIntentionalShutdown = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)
    private val sessionLock = Any()
    private val recordLifecycleLock = Any()
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
            isIntentionalShutdown.set(false)

            val thread = Thread({
                try {
                    synchronized(recordLifecycleLock) {
                        if (record.recordingState != 3 /* RECORDSTATE_RECORDING */) {
                            record.startRecording()
                        }
                    }
                    loopBody(record) { isRunning.get() && !Thread.currentThread().isInterrupted }
                } catch (t: Throwable) {
                    logE("Unexpected exception in audio capture worker", t)
                    try {
                        onError?.invoke(t)
                    } catch (cbEx: Throwable) {
                        logE("Error in onError callback", cbEx)
                    }
                } finally {
                    performRelease()
                    val unexpected = !isIntentionalShutdown.get()
                    synchronized(sessionLock) {
                        isRunning.set(false)
                        if (workerThread === Thread.currentThread()) {
                            workerThread = null
                        }
                    }
                    if (unexpected) {
                        try {
                            onUnexpectedTermination?.invoke()
                        } catch (t: Throwable) {
                            logE("Error in onUnexpectedTermination callback", t)
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
        isIntentionalShutdown.set(true)

        synchronized(sessionLock) {
            if (!isRunning.get() && workerThread == null) {
                // If worker already exited before stopAndAwait, ensure release occurred
                performRelease()
                return true
            }
            isRunning.set(false)
            threadToJoin = workerThread
        }

        // Call stop under recordLifecycleLock outside sessionLock to unblock reader
        synchronized(recordLifecycleLock) {
            try {
                if (record.recordingState == 3 /* RECORDSTATE_RECORDING */) {
                    record.stop()
                } else {
                    // Already stopped
                }
            } catch (t: Throwable) {
                logW("Error stopping audio recorder in stopAndAwait", t)
            }
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
            synchronized(recordLifecycleLock) {
                try {
                    if (record.recordingState == 3 /* RECORDSTATE_RECORDING */) {
                        record.stop()
                    } else {
                        // Already stopped
                    }
                } catch (t: Throwable) {
                    logW("Error stopping audio recorder in performRelease", t)
                }
                try {
                    record.release()
                } catch (t: Throwable) {
                    logW("Error releasing audio recorder in performRelease", t)
                }
            }
        }
    }
}
