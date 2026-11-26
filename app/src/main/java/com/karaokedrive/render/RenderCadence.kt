package com.karaokedrive.render

import com.karaokedrive.karaoke.KaraokeUiState
import com.karaokedrive.karaoke.Status

/** Quand redessiner la scène : à chaque frame, ou seulement quand l'état affiché change. */
object RenderCadence {

    /**
     * true tant que des paroles défilent : leur position vient de l'horloge et non de l'état,
     * il faut redessiner à chaque frame. Sinon (attente, écoute, morceau sans paroles,
     * recherche des paroles…) l'image ne dépend que de l'état : inutile de la refaire.
     */
    fun isAnimated(state: KaraokeUiState): Boolean = state.status == Status.SINGING && state.lyrics != null
}
