package com.karaokedrive.ui

/** Actions déclenchées par l'interface du téléphone (regroupées pour ne pas multiplier les paramètres). */
data class KaraokeActions(
    val onListen: () -> Unit,
    val onStop: () -> Unit,
    val onRescan: () -> Unit,
    val onCalibrate: (Int) -> Unit,
    val onToggleBall: () -> Unit,
    val onToggleEffects: () -> Unit,
    val onToggleTranslation: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onOpenFavorite: (String) -> Unit,
    val onDeleteFavorite: (String) -> Unit,
    val onPracticePlayPause: () -> Unit,
    val onPracticeSeek: (Long) -> Unit,
    val onPracticeRestart: () -> Unit,
    val onPracticeSpeed: () -> Unit,
)
