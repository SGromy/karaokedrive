package com.karaokedrive.karaoke

import android.content.Context
import android.content.SharedPreferences

/** Réglages mémorisés sur le téléphone (décalage des paroles, boule, effets, traduction). */
class KaraokeSettings(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("karaoke", Context.MODE_PRIVATE)

    var calibrationMs: Long
        get() = prefs.getLong(CALIBRATION, DEFAULT_CALIBRATION_MS)
        set(value) = prefs.edit().putLong(CALIBRATION, value).apply()

    var showBall: Boolean
        get() = prefs.getBoolean(SHOW_BALL, true)
        set(value) = prefs.edit().putBoolean(SHOW_BALL, value).apply()

    var showEffects: Boolean
        get() = prefs.getBoolean(SHOW_EFFECTS, true)
        set(value) = prefs.edit().putBoolean(SHOW_EFFECTS, value).apply()

    var showTranslation: Boolean
        get() = prefs.getBoolean(AUTO_TRANSLATE, true)
        set(value) = prefs.edit().putBoolean(AUTO_TRANSLATE, value).apply()

    companion object {
        /** Décalage mesuré sur le premier test réel (radio via TV) : paroles en retard de ~1,2 s. */
        const val DEFAULT_CALIBRATION_MS = 1_200L
        private const val CALIBRATION = "calibration_ms"
        private const val SHOW_BALL = "show_ball"
        private const val SHOW_EFFECTS = "show_effects"
        private const val AUTO_TRANSLATE = "auto_translate"
    }
}
