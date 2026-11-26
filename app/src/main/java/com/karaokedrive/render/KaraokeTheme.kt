package com.karaokedrive.render

import android.graphics.Color

/** Palette « scène néon » partagée par le téléphone et l'écran de la voiture. */
object KaraokeTheme {
    val stageTop = Color.parseColor("#1B1045")
    val stageBottom = Color.parseColor("#07051A")
    val spotlight = Color.parseColor("#3A1F7A")

    val sungStart = Color.parseColor("#FF3D8B")   // rose néon
    val sungEnd = Color.parseColor("#FFC247")     // ambre
    val unsung = Color.parseColor("#F4F0FF")
    val dim = Color.parseColor("#8C84B8")

    val shadow = Color.parseColor("#000000")
    /** Traduction : ambre doux, lisible sans concurrencer la ligne chantée. */
    val translation = Color.parseColor("#E8C98A")

    /** Onde néon quand la pochette est terne ou absente : cyan et magenta. */
    val waveStart = Color.parseColor("#3FE6FF")
    val waveEnd = Color.parseColor("#FF4FD8")

    const val UNSUNG_ALPHA = 150
    const val CONTEXT_ALPHA = 110
}
