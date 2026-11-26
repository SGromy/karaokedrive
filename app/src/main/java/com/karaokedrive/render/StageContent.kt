package com.karaokedrive.render

import android.graphics.Canvas
import android.graphics.RectF
import com.karaokedrive.karaoke.KaraokeController
import com.karaokedrive.karaoke.KaraokeFrame
import com.karaokedrive.karaoke.KaraokeUiState
import com.karaokedrive.karaoke.Status

/** Choisit quoi dessiner selon l'état : paroles synchronisées ou message d'attente. */
/**
 * @param drawWaitingScreens false sur le téléphone : repos, écoute et erreurs y sont affichés
 *   par l'interface (grand bouton rond), la scène ne dessine alors que le fond.
 * @param effects onde et visualiseur animés : téléphone seulement (pas d'animation
 *   de fond sur l'écran de la voiture, sous les yeux du conducteur).
 */
fun KaraokeRenderer.drawState(
    canvas: Canvas, area: RectF, state: KaraokeUiState,
    drawWaitingScreens: Boolean = true, effects: Boolean = false,
) {
    val lyrics = state.lyrics
    val position = KaraokeController.positionMs()
    if (state.status == Status.SINGING && lyrics != null && position != null) {
        val source = when {
            state.isPractice -> "entraînement · ${formatSpeed(state.practiceSpeed)}"
            state.followsApp -> "via ${state.sourceLabel}"
            state.sourceLabel != null -> "micro · ${state.sourceLabel} détecté"
            else -> "micro"
        }
        draw(
            canvas, area, lyrics, KaraokeFrame.compute(lyrics.lines, position),
            source, state.cover, state.translations, state.showBall, effects,
        )
        return
    }
    if (!drawWaitingScreens && state.status in PHONE_WAITING) {
        drawBackdrop(canvas, area, logo)
        return
    }
    val (title, subtitle) = when (state.status) {
        Status.IDLE -> "KaraokeDrive" to "Lancez l'écoute depuis le téléphone ou ce bouton"
        Status.LISTENING -> "J'écoute la radio…" to "Identification du morceau en cours"
        Status.NO_MATCH -> "Morceau non reconnu" to "Nouvel essai dans un instant"
        Status.NO_LYRICS -> "Pas de paroles synchronisées" to (state.message ?: state.trackLabel)
        Status.ERROR -> "Oups" to state.message
        Status.SINGING -> (if (lyrics == null) "Recherche des paroles…" else "Synchronisation…") to state.trackLabel
    }
    // Pochette si le morceau est connu, sinon le logo pendant l'attente et l'écoute
    val art = state.cover ?: logo.takeIf { state.status != Status.NO_LYRICS }
    drawMessage(canvas, area, title, subtitle, art)
}

private val PHONE_WAITING = setOf(Status.IDLE, Status.LISTENING, Status.NO_MATCH, Status.ERROR)

/** « 1× », « 0,75× »… */
fun formatSpeed(speed: Float): String =
    (if (speed % 1f == 0f) "%.0f" else "%.2f").format(speed).trimEnd('0').trimEnd(',', '.') + "×"
