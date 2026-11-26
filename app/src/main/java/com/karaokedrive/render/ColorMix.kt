package com.karaokedrive.render

import kotlin.math.roundToInt

/** Mélanges de couleurs ARGB (calcul pur, testable sans Android). */
object ColorMix {

    /** [color] éclaircie vers le blanc : 0 = inchangée, 1 = blanc. Le résultat est opaque. */
    fun withWhite(color: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        fun mix(channel: Int) = (channel + (255 - channel) * a).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(color shr 16 and 0xFF) shl 16) or (mix(color shr 8 and 0xFF) shl 8) or mix(color and 0xFF)
    }

    /** [color] avec l'opacité [alpha] (0..255). */
    fun withAlpha(color: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)
}
