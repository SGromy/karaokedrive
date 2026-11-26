package com.karaokedrive.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max

/**
 * Dessine la pochette : vignette arrondie et fond flouté. Le flou est obtenu en réduisant
 * l'image à quelques pixels puis en l'agrandissant avec filtrage, ce qui marche aussi sur
 * le canvas logiciel de l'écran Android Auto (pas besoin de RenderEffect).
 */
internal class CoverArtPainter {

    private var source: Bitmap? = null
    private var blurred: Bitmap? = null
    /** Shader de la vignette, gardé tant que la pochette ne change pas (seule sa position bouge). */
    private var shaderSource: Bitmap? = null
    private var thumbnailShader: BitmapShader? = null

    private val backdropPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = BACKDROP_ALPHA }
    private val thumbnailPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val thumbnailMatrix = Matrix()
    private val destination = RectF()

    /** Fond de scène teinté par la pochette, recadrée pour couvrir [area]. */
    fun drawBackdrop(canvas: Canvas, area: RectF, cover: Bitmap) {
        val tiny = blurredFor(cover)
        val scale = max(area.width() / tiny.width, area.height() / tiny.height)
        val width = tiny.width * scale
        val height = tiny.height * scale
        destination.set(area.centerX() - width / 2, area.centerY() - height / 2, area.centerX() + width / 2, area.centerY() + height / 2)
        canvas.save()
        canvas.clipRect(area)
        canvas.drawBitmap(tiny, null, destination, backdropPaint)
        canvas.restore()
    }

    /** Vignette carrée aux coins arrondis dans [bounds]. */
    fun drawThumbnail(canvas: Canvas, bounds: RectF, cover: Bitmap) {
        val shader = shaderFor(cover)
        val scale = max(bounds.width() / cover.width, bounds.height() / cover.height)
        thumbnailMatrix.setScale(scale, scale)
        thumbnailMatrix.postTranslate(bounds.left, bounds.top)
        shader.setLocalMatrix(thumbnailMatrix)
        thumbnailPaint.shader = shader
        val radius = bounds.width() * 0.14f
        canvas.drawRoundRect(bounds, radius, radius, thumbnailPaint)
    }

    private fun shaderFor(cover: Bitmap): BitmapShader {
        val cached = thumbnailShader
        if (cover === shaderSource && cached != null) return cached
        shaderSource = cover
        return BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { thumbnailShader = it }
    }

    private fun blurredFor(cover: Bitmap): Bitmap {
        if (cover !== source || blurred == null) {
            source = cover
            blurred = Bitmap.createScaledBitmap(cover, BLUR_PX, BLUR_PX, true)
        }
        return requireNotNull(blurred)
    }

    private companion object {
        /** Plus c'est petit, plus c'est flou. */
        const val BLUR_PX = 12
        /** Discret : la pochette colore l'ambiance sans gêner la lecture des paroles. */
        const val BACKDROP_ALPHA = 70
    }
}
