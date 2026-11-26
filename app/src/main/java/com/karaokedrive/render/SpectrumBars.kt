package com.karaokedrive.render

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Hauteurs d'un visualiseur « égaliseur » : basses au centre, aigus vers les bords, en miroir.
 * Pas de vrai son (micro fermé entre deux reconnaissances, audio des autres apps inaccessible) :
 * les barres bougent d'elles-mêmes et pompent au rythme des mots chantés. Calcul pur et testable.
 */
object SpectrumBars {

    /** Hauteurs (0..1) des [count] barres à l'instant [timeMs] ; [energy] (0..1) = pulsation des mots. */
    fun heights(count: Int, timeMs: Long, energy: Float): FloatArray {
        val t = timeMs / 1000f
        val middle = (count - 1) / 2f
        val pulse = energy.coerceIn(0f, 1f)
        return FloatArray(count) { i ->
            val distance = abs(i - middle)
            // 0 au centre (basses) → 1 au bord (aigus)
            val f = if (middle > 0f) distance / middle else 0f
            val seed = frac(floor(distance) * GOLDEN + 0.13f)
            val speed = 1.6f + 2.4f * f + seed
            val motion = 0.6f * wave(t * speed + seed * 9f) + 0.4f * wave(t * speed * 1.7f + seed * 4f)
            val profile = 1f - PROFILE_SLOPE * f
            // Les basses réagissent plus fort aux mots (comme la grosse caisse)
            val gain = CALM_GAIN + (1f - CALM_GAIN) * pulse * (1f - 0.5f * f)
            (profile * (0.4f + 0.6f * motion) * gain).coerceIn(MIN_HEIGHT, 1f)
        }
    }

    private fun wave(x: Float) = 0.5f + 0.5f * sin(x)

    private fun frac(v: Float) = v - floor(v)

    private const val GOLDEN = 0.618034f
    private const val PROFILE_SLOPE = 0.5f
    /** Hauteur relative sans pulsation (entre les lignes, pendant les intros). */
    private const val CALM_GAIN = 0.5f
    private const val MIN_HEIGHT = 0.04f
}
