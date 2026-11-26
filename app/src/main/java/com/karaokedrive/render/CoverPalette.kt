package com.karaokedrive.render

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Couleurs d'accent tirées de la pochette (onde néon). Calcul pur sur des pixels ARGB,
 * sans API Android, pour être testable.
 */
object CoverPalette {

    /**
     * Les deux teintes vives dominantes de la pochette, ravivées façon néon ;
     * null si la pochette est terne (noir et blanc, gris…).
     */
    fun accents(pixels: IntArray): Pair<Int, Int>? {
        val vivid = pixels.map(::toHsv).filter { it[1] >= MIN_SATURATION && it[2] >= MIN_VALUE }
        if (vivid.isEmpty()) return null
        // Teintes regroupées par secteurs de 30°, pondérées par leur éclat
        val bins = vivid.groupBy { (floor(it[0] / BIN_DEGREES).toInt()) % BINS }
        val ranked = bins.entries.sortedByDescending { (_, hsv) -> hsv.sumOf { (it[1] * it[2]).toDouble() } }
        val first = ranked.first()
        val second = ranked.drop(1).firstOrNull { binDistance(it.key, first.key) >= MIN_BIN_GAP }
        val main = first.value.maxBy { it[1] * it[2] }
        val other = second?.value?.maxBy { it[1] * it[2] }
        val otherHue = other?.get(0) ?: ((main[0] + NEIGHBOUR_SHIFT) % 360f)
        return neon(main[0], main[1]) to neon(otherHue, other?.get(1) ?: main[1])
    }

    /** [teinte 0..360, saturation 0..1, valeur 0..1] d'une couleur ARGB. */
    fun toHsv(color: Int): FloatArray {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val delta = max - minOf(r, g, b)
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta).mod(6f))
            max == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        return floatArrayOf(hue, if (max == 0f) 0f else delta / max, max)
    }

    /** Couleur opaque à partir de la teinte (0..360), de la saturation et de la valeur (0..1). */
    fun fromHsv(hue: Float, saturation: Float, value: Float): Int {
        val c = value * saturation
        val x = c * (1f - abs((hue / 60f).mod(2f) - 1f))
        val m = value - c
        val (r, g, b) = when ((hue / 60f).toInt().mod(6)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    private fun neon(hue: Float, saturation: Float) = fromHsv(hue, saturation.coerceIn(NEON_MIN_SATURATION, NEON_MAX_SATURATION), 1f)

    private fun binDistance(a: Int, b: Int) = abs(a - b).let { min(it, BINS - it) }

    private const val MIN_SATURATION = 0.25f
    private const val MIN_VALUE = 0.25f
    private const val BINS = 12
    private const val BIN_DEGREES = 360f / BINS
    /** Deux secteurs d'écart (≥ 60°) pour que les deux couleurs se distinguent. */
    private const val MIN_BIN_GAP = 2
    /** Pochette d'une seule teinte : seconde couleur voisine sur le cercle chromatique. */
    private const val NEIGHBOUR_SHIFT = 40f
    private const val NEON_MIN_SATURATION = 0.6f
    private const val NEON_MAX_SATURATION = 0.9f
}
