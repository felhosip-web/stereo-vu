package com.example.stereovu

internal enum class AudioCaptureSource(
    val preferenceValue: String,
    val displayName: String
) {
    INTERNAL_PLAYBACK("internal_playback", "Belső lejátszás hangja"),
    MICROPHONE("microphone", "Mikrofon");

    companion object {
        fun fromPreferenceValue(value: String?): AudioCaptureSource? =
            values().firstOrNull { it.preferenceValue == value }
    }
}

internal enum class AudioCaptureRequestError(val userMessage: String) {
    MISSING_SOURCE("A hangforrás hiányzik. Indítsd el a rögzítést az alkalmazásból."),
    INTERNAL_CAPTURE_UNSUPPORTED(
        "A belső hang rögzítéséhez Android 10 vagy újabb szükséges. Válassz mikrofont."
    ),
    PROJECTION_CONSENT_DENIED(
        "A belső hang rögzítéséhez szükséges engedélyt nem adtad meg. A mikrofon nem indul el."
    ),
    PROJECTION_RESULT_MISSING(
        "A MediaProjection engedély adatai hiányoznak. Indítsd újra a rögzítést."
    )
}

internal enum class AudioCaptureFailureStage {
    INITIALIZATION,
    RUNTIME
}

internal fun AudioCaptureSource.activeStatusMessage(): String =
    "Forrás: $displayName"

internal fun AudioCaptureSource.failureMessage(stage: AudioCaptureFailureStage): String =
    when (this) {
        AudioCaptureSource.INTERNAL_PLAYBACK -> when (stage) {
            AudioCaptureFailureStage.INITIALIZATION ->
                "A belső hang rögzítése nem indítható el. Ellenőrizd az engedélyt és próbáld újra."
            AudioCaptureFailureStage.RUNTIME ->
                "A belső hangrögzítés megszakadt. A mikrofon nem indul el."
        }
        AudioCaptureSource.MICROPHONE -> when (stage) {
            AudioCaptureFailureStage.INITIALIZATION ->
                "A mikrofon rögzítése nem indítható el. Ellenőrizd a mikrofonengedélyt."
            AudioCaptureFailureStage.RUNTIME ->
                "A mikrofonrögzítés megszakadt."
        }
    }

internal fun validateAudioCaptureRequest(
    source: AudioCaptureSource?,
    sdkInt: Int,
    resultCode: Int?,
    hasCaptureIntent: Boolean
): AudioCaptureRequestError? {
    if (source == null) return AudioCaptureRequestError.MISSING_SOURCE
    if (source == AudioCaptureSource.MICROPHONE) return null
    if (sdkInt < 29) return AudioCaptureRequestError.INTERNAL_CAPTURE_UNSUPPORTED
    if (resultCode != -1) return AudioCaptureRequestError.PROJECTION_CONSENT_DENIED
    if (!hasCaptureIntent) return AudioCaptureRequestError.PROJECTION_RESULT_MISSING
    return null
}

internal inline fun <T> createRecordForSource(
    source: AudioCaptureSource,
    createInternalPlaybackRecord: () -> T,
    createMicrophoneRecord: () -> T
): T = when (source) {
    AudioCaptureSource.INTERNAL_PLAYBACK -> createInternalPlaybackRecord()
    AudioCaptureSource.MICROPHONE -> createMicrophoneRecord()
}
