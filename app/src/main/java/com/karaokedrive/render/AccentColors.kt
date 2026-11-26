package com.karaokedrive.render

import android.graphics.Bitmap

/**
 * Couleurs d'accent de la pochette affichée (onde et visualiseur), recalculées seulement
 * quand la pochette change ; cyan et magenta si elle est terne ou absente.
 */
internal class AccentColors {

    private var source: Bitmap? = null
    private var colors = DEFAULT

    fun of(art: Bitmap?): Pair<Int, Int> {
        if (art !== source) {
            source = art
            colors = art?.let(::extract) ?: DEFAULT
        }
        return colors
    }

    private fun extract(art: Bitmap): Pair<Int, Int>? = runCatching {
        val tiny = Bitmap.createScaledBitmap(art, SAMPLE_PX, SAMPLE_PX, true)
        val pixels = IntArray(SAMPLE_PX * SAMPLE_PX).also { tiny.getPixels(it, 0, SAMPLE_PX, 0, 0, SAMPLE_PX, SAMPLE_PX) }
        CoverPalette.accents(pixels)
    }.getOrNull()

    private companion object {
        const val SAMPLE_PX = 16
        val DEFAULT = KaraokeTheme.waveStart to KaraokeTheme.waveEnd
    }
}
