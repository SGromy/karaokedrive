package com.karaokedrive.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.max

/**
 * Dessine l'onde néon derrière la pochette, aux couleurs de celle-ci ([AccentColors]). Le halo est fait
 * de trois traits superposés (large et pâle → fin et clair) : pas de flou, donc ça marche
 * aussi sur le canvas logiciel de l'écran Android Auto.
 */
internal class NeonWavePainter {

    private data class ShaderKey(val centerX: Float, val period: Float, val colors: Pair<Int, Int>)

    private val path = Path()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private var shaderKey: ShaderKey? = null
    private var glowShader: Shader? = null
    private var coreShader: Shader? = null

    /**
     * Onde horizontale de [left] à [right], centrée verticalement sur [centerY] et symétrique
     * autour de [centerX] (le centre de la pochette) : elle part de la pochette et ondule vers
     * l'extérieur, vers la gauche à gauche et vers la droite à droite.
     * [amplitude] = hauteur max d'un pic en pixels ; [energy] (0..1) fait pulser les pics.
     */
    fun draw(
        canvas: Canvas, left: Float, right: Float, centerX: Float, centerY: Float,
        amplitude: Float, energy: Float, colors: Pair<Int, Int>, timeMs: Long,
    ) {
        if (right <= left || amplitude <= 0f) return
        buildPath(left, right, centerX, centerY, amplitude, energy, timeMs)
        updateShaders(centerX, amplitude, colors)
        stroke(canvas, glowShader, amplitude * 0.075f, GLOW_ALPHA)
        stroke(canvas, glowShader, amplitude * 0.03f, HALO_ALPHA)
        stroke(canvas, coreShader, max(1.5f, amplitude * 0.009f), 255)
    }

    private fun stroke(canvas: Canvas, shader: Shader?, width: Float, alpha: Int) {
        strokePaint.shader = shader
        strokePaint.strokeWidth = width
        strokePaint.alpha = alpha
        canvas.drawPath(path, strokePaint)
    }

    private fun buildPath(left: Float, right: Float, centerX: Float, centerY: Float, amplitude: Float, energy: Float, timeMs: Long) {
        // Une période de l'onde = 4 amplitudes : la densité des pics suit la taille de la pochette.
        // On échantillonne selon la distance au centre : les deux côtés sont le reflet l'un de l'autre,
        // et comme les salves dérivent vers les distances croissantes, elles s'éloignent de la pochette.
        val period = amplitude * PERIOD_IN_AMPLITUDES
        val step = max(2f, amplitude * 0.008f)
        fun yAt(x: Float) = centerY - amplitude * NeonWave.sample(abs(x - centerX) / period, timeMs, energy)
        path.rewind()
        var x = left
        path.moveTo(x, yAt(x))
        while (x < right) {
            x = minOf(x + step, right)
            path.lineTo(x, yAt(x))
        }
    }

    /**
     * Dégradé qui alterne les deux couleurs le long de l'onde (comme les salves cyan/magenta),
     * en miroir autour du centre de la pochette pour que les deux côtés se répondent.
     */
    private fun updateShaders(centerX: Float, amplitude: Float, colors: Pair<Int, Int>) {
        val key = ShaderKey(centerX, amplitude * COLOR_PERIOD_IN_AMPLITUDES, colors)
        if (key == shaderKey) return
        shaderKey = key
        val (start, end) = colors
        glowShader = LinearGradient(centerX, 0f, centerX + key.period, 0f, start, end, Shader.TileMode.MIRROR)
        coreShader = LinearGradient(
            centerX, 0f, centerX + key.period, 0f,
            ColorMix.withWhite(start, CORE_WHITENESS), ColorMix.withWhite(end, CORE_WHITENESS), Shader.TileMode.MIRROR,
        )
    }

    private companion object {
        const val PERIOD_IN_AMPLITUDES = 4f
        const val COLOR_PERIOD_IN_AMPLITUDES = 0.8f
        const val GLOW_ALPHA = 45
        const val HALO_ALPHA = 110
        /** Cœur du trait presque blanc, teinté : effet tube néon. */
        const val CORE_WHITENESS = 0.45f
    }
}
