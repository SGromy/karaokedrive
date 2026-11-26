package com.karaokedrive.karaoke

import android.graphics.Bitmap
import com.karaokedrive.lyrics.Lyrics

enum class Status { IDLE, LISTENING, NO_MATCH, NO_LYRICS, SINGING, ERROR }

/** Tout ce qu'affichent le téléphone et l'écran de la voiture. */
data class KaraokeUiState(
    /** [Status.SINGING] sans [lyrics] : morceau connu, recherche de ses paroles en cours. */
    val status: Status = Status.IDLE,
    val lyrics: Lyrics? = null,
    val message: String? = null,
    val calibrationMs: Long = KaraokeSettings.DEFAULT_CALIBRATION_MS,
    /** Morceau reconnu sans paroles disponibles : on affiche au moins le titre. */
    val trackLabel: String? = null,
    /** Application média détectée sur le téléphone (null = aucune, source micro seule). */
    val sourceLabel: String? = null,
    /** Synchro calée sur la position donnée par l'application (sans micro ni décalage). */
    val followsApp: Boolean = false,
    /** Pochette du morceau en cours (null tant qu'elle n'est pas chargée ou introuvable). */
    val cover: Bitmap? = null,
    /** Traduction française de chaque ligne (paroles anglaises), null si non traduites. */
    val translations: List<String?>? = null,
    /** Boule qui rebondit sur les mots (réglage mémorisé). */
    val showBall: Boolean = true,
    /** Onde néon et visualiseur animés derrière la pochette et les paroles (réglage mémorisé). */
    val showEffects: Boolean = true,
    /** Traduction française automatique des paroles anglaises (réglage mémorisé). */
    val showTranslation: Boolean = true,
    /** Morceau affiché présent dans les favoris. */
    val isFavorite: Boolean = false,
    /** Mode entraînement : paroles d'un favori sans musique, sur une horloge interne. */
    val isPractice: Boolean = false,
    val practicePlaying: Boolean = false,
    val practiceSpeed: Float = 1f,
) {
    /** État de départ (Stop) : on ne garde que les réglages. */
    fun reset(): KaraokeUiState = KaraokeUiState(
        calibrationMs = calibrationMs, showBall = showBall, showEffects = showEffects, showTranslation = showTranslation,
    )
}
