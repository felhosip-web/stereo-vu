package com.example.stereovu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AudioCaptureSourceTest {

    @Test
    fun validProjectionResultSelectsInternalPlayback() {
        assertNull(
            validateAudioCaptureRequest(
                source = AudioCaptureSource.INTERNAL_PLAYBACK,
                sdkInt = 29,
                resultCode = -1,
                hasCaptureIntent = true
            )
        )
    }

    @Test
    fun nullCaptureIntentCannotSelectMicrophoneAutomatically() {
        assertEquals(
            AudioCaptureRequestError.PROJECTION_RESULT_MISSING,
            validateAudioCaptureRequest(
                source = AudioCaptureSource.INTERNAL_PLAYBACK,
                sdkInt = 34,
                resultCode = -1,
                hasCaptureIntent = false
            )
        )
    }

    @Test
    fun deniedProjectionConsentDoesNotStartCapture() {
        assertEquals(
            AudioCaptureRequestError.PROJECTION_CONSENT_DENIED,
            validateAudioCaptureRequest(
                source = AudioCaptureSource.INTERNAL_PLAYBACK,
                sdkInt = 34,
                resultCode = 0,
                hasCaptureIntent = true
            )
        )
    }

    @Test
    fun missingServiceExtrasAndRestartCannotSelectMicrophone() {
        assertNull(AudioCaptureSource.fromPreferenceValue(null))
        assertNull(AudioCaptureSource.fromPreferenceValue("unknown"))
        assertEquals(
            AudioCaptureRequestError.MISSING_SOURCE,
            validateAudioCaptureRequest(
                source = null,
                sdkInt = 34,
                resultCode = null,
                hasCaptureIntent = false
            )
        )
    }

    @Test
    fun internalCaptureIsUnsupportedBeforeAndroidTen() {
        assertEquals(
            AudioCaptureRequestError.INTERNAL_CAPTURE_UNSUPPORTED,
            validateAudioCaptureRequest(
                source = AudioCaptureSource.INTERNAL_PLAYBACK,
                sdkInt = 28,
                resultCode = -1,
                hasCaptureIntent = true
            )
        )
    }

    @Test
    fun explicitMicrophoneSelectionUsesMicrophoneFactory() {
        val playbackCalls = AtomicInteger()
        val microphoneCalls = AtomicInteger()

        val selected = createRecordForSource(
            source = AudioCaptureSource.MICROPHONE,
            createInternalPlaybackRecord = {
                playbackCalls.incrementAndGet()
                "playback"
            },
            createMicrophoneRecord = {
                microphoneCalls.incrementAndGet()
                "microphone"
            }
        )

        assertEquals("microphone", selected)
        assertEquals(0, playbackCalls.get())
        assertEquals(1, microphoneCalls.get())
    }

    @Test
    fun explicitMicrophoneSelectionDoesNotRequireProjectionConsent() {
        assertNull(
            validateAudioCaptureRequest(
                source = AudioCaptureSource.MICROPHONE,
                sdkInt = 34,
                resultCode = null,
                hasCaptureIntent = false
            )
        )
    }

    @Test
    fun playbackInitializationFailureDoesNotFallBackToMicrophone() {
        val microphoneCalls = AtomicInteger()

        assertThrows(IllegalStateException::class.java) {
            createRecordForSource(
                source = AudioCaptureSource.INTERNAL_PLAYBACK,
                createInternalPlaybackRecord = {
                    throw IllegalStateException("simulated AudioRecord initialization failure")
                },
                createMicrophoneRecord = {
                    microphoneCalls.incrementAndGet()
                    "microphone"
                }
            )
        }

        assertEquals(0, microphoneCalls.get())
    }

    @Test
    fun activeSourceAndErrorsHaveUserVisibleText() {
        assertTrue(AudioCaptureSource.INTERNAL_PLAYBACK.activeStatusMessage().contains("Belső"))
        assertTrue(AudioCaptureSource.MICROPHONE.activeStatusMessage().contains("Mikrofon"))
        assertTrue(AudioCaptureRequestError.PROJECTION_RESULT_MISSING.userMessage.isNotBlank())
        assertTrue(AudioCaptureRequestError.PROJECTION_CONSENT_DENIED.userMessage.isNotBlank())
        assertTrue(
            AudioCaptureSource.INTERNAL_PLAYBACK
                .failureMessage(AudioCaptureFailureStage.INITIALIZATION)
                .contains("A mikrofon nem indul el")
        )
        assertFalse(
            AudioCaptureSource.MICROPHONE
                .failureMessage(AudioCaptureFailureStage.RUNTIME)
                .isBlank()
        )
    }
}
