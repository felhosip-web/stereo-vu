package com.example.stereovu

/**
 * Coordinates capture session state, active instance identity, and generation tokens.
 *
 * Guarantees that:
 * 1. Only an active session matching the current monotonically increasing generation token
 *    can trigger unexpected termination handling.
 * 2. If a queued termination runnable is delayed on the main thread, it revalidates under lock
 *    that no replacement session has started in the interim before taking action.
 * 3. An obsolete session callback can never reset levels or stop the service.
 */
class CaptureSessionCoordinator(
    private val mainThreadPoster: (Runnable) -> Unit,
    private val resetLevelsAction: () -> Unit,
    private val stopServiceAction: () -> Unit
) {
    val lock = Any()
    var currentSession: AudioCaptureSession? = null
        private set
    var currentGeneration: Long = 0L
        private set

    /**
     * Registers a new session with an incremented generation token.
     * Returns the generation token assigned to this session.
     */
    fun attachSession(session: AudioCaptureSession): Long {
        synchronized(lock) {
            currentGeneration++
            currentSession = session
            return currentGeneration
        }
    }

    /**
     * Invoked when a session terminates unexpectedly.
     * Checks if the session and generation are still current, and schedules revalidation
     * on the main thread before resetting levels and stopping the service.
     */
    fun onSessionUnexpectedTermination(session: AudioCaptureSession, sessionGeneration: Long) {
        val shouldSchedule = synchronized(lock) {
            if (currentSession === session && currentGeneration == sessionGeneration) {
                currentSession = null
                true
            } else {
                false
            }
        }

        if (shouldSchedule) {
            mainThreadPoster(Runnable {
                executeMainThreadTermination(sessionGeneration)
            })
        }
    }

    /**
     * Executes on the main thread. Revalidates under lock that no replacement session
     * has taken ownership before resetting levels and stopping the service.
     * Returns true if termination was executed, false if obsolete/superseded.
     */
    fun executeMainThreadTermination(expectedGeneration: Long): Boolean {
        val stillValid = synchronized(lock) {
            currentGeneration == expectedGeneration && currentSession == null
        }

        if (stillValid) {
            resetLevelsAction()
            stopServiceAction()
            return true
        }
        return false
    }

    /**
     * Detaches and returns the current session during intentional service shutdown.
     */
    fun detachForShutdown(): AudioCaptureSession? {
        synchronized(lock) {
            val session = currentSession
            currentSession = null
            currentGeneration++ // Invalidate any in-flight callbacks
            return session
        }
    }
}
