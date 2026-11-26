package com.karaokedrive.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Visualiseur en barres verticales, en miroir de part et d'autre d'une ligne horizontale.
 * Les barres sont dessinées dans une petite image puis agrandies avec lissage : on obtient
 * un flou doux sans RenderEffect, qui laisse les paroles lisibles par-dessus.
 */
internal class SpectrumPainter {

    private var small: Bitmap? = null
    private var smallCanvas: Canvas? = null
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val upscalePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = SPECTRUM_ALPHA }
    private val bar = RectF()

    /** Barres dans [bounds] (la ligne miroir est au milieu), aux couleurs [colors] (centre → bords). */
    fun draw(canvas: Canvas, bounds: RectF, energy: Float, colors: Pair<Int, Int>, timeMs: Long) {
        val target = bitmapFor(bounds) ?: return
        val offscreen = smallCanvas ?: return
        target.eraseColor(Color.TRANSPARENT)
        val width = target.width.toFloat()
        val middle = target.height / 2f
        val heights = SpectrumBars.heights(BAR_COUNT, timeMs, energy)
        val slot = width / BAR_COUNT
        val half = slot * BAR_FILL / 2
        heights.forEachIndexed { i, h ->
            val centerX = slot * (i + 0.5f)
            val f = abs(i - (BAR_COUNT - 1) / 2f) / ((BAR_COUNT - 1) / 2f)
            barPaint.color = blend(colors.first, colors.second, f)
            val extent = max(0.5f, h * middle * 0.95f)
            bar.set(centerX - half, middle - extent, centerX + half, middle + extent)
            offscreen.drawRoundRect(bar, half, half, barPaint)
        }
        canvas.drawBitmap(target, null, bounds, upscalePaint)
    }

    private fun bitmapFor(bounds: RectF): Bitmap? {
        val w = (bounds.width() / DOWNSCALE).roundToInt()
        val h = (bounds.height() / DOWNSCALE).roundToInt()
        if (w < 4 || h < 4) return null
        small?.takeIf { it.width == w && it.height == h }?.let { return it }
        small?.recycle()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            small = it
            smallCanvas = Canvas(it)
        }
    }

    private fun blend(from: Int, to: Int, f: Float): Int {
        fun channel(shift: Int) = ((from shr shift and 0xFF) + ((to shr shift and 0xFF) - (from shr shift and 0xFF)) * f).roundToInt()
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private companion object {
        const val BAR_COUNT = 31
        /** Part de chaque emplacement occupée par la barre (le reste = espace entre barres). */
        const val BAR_FILL = 0.62f
        /** Plus l'image intermédiaire est petite, plus le flou est large. */
        const val DOWNSCALE = 7f
        /** ~45 % d'opacité : bien visible, les paroles restent au premier plan. */
        const val SPECTRUM_ALPHA = 120
    }
}
