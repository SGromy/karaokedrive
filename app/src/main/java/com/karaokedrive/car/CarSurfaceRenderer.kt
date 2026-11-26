package com.karaokedrive.car

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.RectF
import android.view.Choreographer
import android.view.Surface
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.karaokedrive.karaoke.KaraokeController
import com.karaokedrive.karaoke.KaraokeUiState
import com.karaokedrive.render.KaraokeRenderer
import com.karaokedrive.render.RenderCadence
import com.karaokedrive.render.drawState

/**
 * Dessine la scène sur la surface fournie par Android Auto : à chaque frame tant que les
 * paroles défilent, sinon seulement quand l'état ou la zone visible change.
 */
class CarSurfaceRenderer(logo: Bitmap?, translationTypeface: Typeface?) : SurfaceCallback, Choreographer.FrameCallback {

    private val renderer = KaraokeRenderer(logo, translationTypeface)
    private var surface: Surface? = null
    private var area = RectF()
    private var isRunning = false
    /** Dernière image envoyée à la surface (null = à redessiner, ex. surface neuve). */
    private var drawnState: KaraokeUiState? = null
    private var drawnArea: RectF? = null

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        surface = container.surface
        drawnState = null
        if (area.isEmpty) area = RectF(0f, 0f, container.width.toFloat(), container.height.toFloat())
        start()
    }

    /** Zone non recouverte par les éléments du système (barre d'actions, etc.). */
    override fun onVisibleAreaChanged(visibleArea: Rect) {
        if (!visibleArea.isEmpty) area = RectF(visibleArea)
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) = release()

    fun release() {
        isRunning = false
        surface = null
        Choreographer.getInstance().removeFrameCallback(this)
    }

    private fun start() {
        if (isRunning) return
        isRunning = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return
        // Surface momentanément invalide : la boucle continue au ralenti au lieu de s'arrêter
        // (sinon une nouvelle surface ne la relancerait jamais : isRunning resterait vrai)
        val target = surface?.takeIf { it.isValid }
        if (target == null) drawnState = null // tout redessiner quand elle revient
        val state = KaraokeController.state.value
        val animated = target != null && RenderCadence.isAnimated(state)
        if (target != null && (animated || state !== drawnState || area != drawnArea)) draw(target, state)
        // Image fixe : on vérifie seulement de temps en temps si l'état a changé, sans dessiner
        if (animated) {
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            Choreographer.getInstance().postFrameCallbackDelayed(this, STATIC_CHECK_MS)
        }
    }

    private fun draw(target: Surface, state: KaraokeUiState) {
        // lockCanvas (logiciel) et non lockHardwareCanvas : si l'hôte Android Auto abandonne
        // la surface sans prévenir, le rendu matériel fait un abort natif impossible à
        // intercepter (« drawRenderNode called on a context with no surface »), alors que
        // le rendu logiciel lève une exception qu'on rattrape.
        runCatching {
            val canvas = target.lockCanvas(null)
            try {
                renderer.drawState(canvas, area, state)
            } finally {
                target.unlockCanvasAndPost(canvas)
            }
            drawnState = state
            drawnArea = area
        }
    }

    private companion object {
        /** Délai maximal avant d'afficher un changement d'état sur un écran fixe. */
        const val STATIC_CHECK_MS = 100L
    }
}
