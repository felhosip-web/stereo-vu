package com.example.stereovu

import android.content.SharedPreferences

/**
 * Migration helper to ensure backwards compatibility of ballistics modes.
 *
 * In earlier versions:
 * 0: Digital Peak
 * 1: Analog VU
 * 2: PPM
 * 3: Custom
 *
 * In the new version with 8 professional ballistics modes:
 * 0: Digital Peak
 * 1: Analog VU
 * 2: PPM
 * 3: True Peak
 * 4: Classic VU
 * 5: BBC PPM
 * 6: Nordic / DIN
 * 7: Custom
 *
 * If a user previously had persisted mode == 3 (Custom), it must be safely and idempotently
 * migrated to mode 7 without silently reinterpreting their setting as True Peak.
 */
object BallisticsModeMigration {
    const val KEY_MODE = "mode"
    const val KEY_MIGRATED = "ballistics_v2_migrated"

    /**
     * Reads the current ballistics mode from SharedPreferences, performing idempotent migration if needed.
     * @param prefs The SharedPreferences instance
     * @return The canonical 0..7 mode index
     */
    fun getMigratedMode(prefs: SharedPreferences): Int {
        val isMigrated = prefs.getBoolean(KEY_MIGRATED, false)
        val rawMode = prefs.getInt(KEY_MODE, 0)

        if (!isMigrated) {
            // Check if we need to migrate raw mode 3 to 7
            val migratedMode = if (rawMode == 3) 7 else rawMode.coerceIn(0, 7)
            prefs.edit()
                .putInt(KEY_MODE, migratedMode)
                .putBoolean(KEY_MIGRATED, true)
                .apply()
            return migratedMode
        }

        return rawMode.coerceIn(0, 7)
    }
}
