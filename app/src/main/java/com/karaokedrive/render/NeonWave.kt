package com.karaokedrive.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Forme de l'onde néon : une ligne presque plate traversée de salves de pics symétriques
 * qui dérivent et « respirent ». Périodique en x (période 1), calcul pur et testable.
 */
object NeonWave {

    /**
     * Hauteur de l'onde (−1..1) au point [x] (en périodes) à l'instant [timeMs].
     * [energy] (0..1) amplifie les pics : elle suit le rebond de la boule sur les mots.
     */
    fun sample(x: Float, timeMs: Long, energy: Float): Float {
        val t = timeMs / 1000f
        var y = 0f
        for (i in 0 until BURSTS) {
            val seed = frac(i * GOLDEN)
            val dx = wrap(x - frac(seed + t * DRIFT_PER_S))
            val sigma = MIN_SIGMA + SIGMA_RANGE * frac(seed * 7.13f)
            if (dx * dx > 16f * sigma * sigma) continue // salve trop loin : contribution nulle
            val breathing = 0.5f + 0.5f * sin(t * (1.1f + 0.37f * i) + i * 2.3f)
            val amplitude = if (i % 3 == 2) 0.12f + 0.18f * breathing else 0.55f + 0.45f * breathing
            val envelope = exp(-0.5f * (dx / sigma) * (dx / sigma))
            y += amplitude * envelope * cos(TWO_PI * dx / (sigma * CARRIER_PERIOD))
        }
        y += RIPPLE * sin(TWO_PI * RIPPLE_WAVES * x + t * 5f)
        val gain = BASE_GAIN + (1f - BASE_GAIN) * energy.coerceIn(0f, 1f)
        return (y * gain).coerceIn(-1f, 1f)
    }

    private fun frac(v: Float) = v - floor(v)

    /** Écart ramené dans [−0,5 ; 0,5] : l'onde se raccorde d'une période à l'autre. */
    private fun wrap(d: Float) = d - floor(d + 0.5f)

    private const val BURSTS = 16
    private const val GOLDEN = 0.618034f
    private const val DRIFT_PER_S = 0.035f
    private const val MIN_SIGMA = 0.005f
    private const val SIGMA_RANGE = 0.01f
    /** Période de l'oscillation dans une salve, en écarts-types : 1,4 donne des pics serrés. */
    private const val CARRIER_PERIOD = 1.4f
    private const val RIPPLE = 0.025f
    private const val RIPPLE_WAVES = 40
    /** Amplitude au repos (entre deux mots, pendant les intros) par rapport au maximum. */
    private const val BASE_GAIN = 0.45f
    private const val TWO_PI = (2 * PI).toFloat()
}
