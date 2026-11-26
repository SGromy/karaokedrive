package com.karaokedrive.translate

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.karaokedrive.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sur le téléphone (Google ML Kit) : détection de la langue des paroles, et traduction
 * anglais → français de secours quand l'IA n'est pas joignable. Le modèle (~30 Mo) est
 * téléchargé une fois, puis tout fonctionne hors connexion.
 */
class LyricsTranslator {

    private val identifier = LanguageIdentification.getClient()
    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.FRENCH)
            .build(),
    )
    /** Téléchargement sur n'importe quel réseau : il doit être prêt avant de partir en voiture. */
    private val conditions = DownloadConditions.Builder().build()

    /** Lance le téléchargement du modèle en avance (au démarrage de l'app). */
    fun prepare() {
        translator.downloadModelIfNeeded(conditions)
            .addOnFailureListener { Log.w(TAG, "modèle de traduction non téléchargé : ${it.message}") }
    }

    /** Code de langue des paroles (« en », « es », « fr »…), « und » si inconnue. */
    suspend fun detectLanguage(lines: List<String>): String =
        identifier.identifyLanguage(LineTranslation.detectionSample(lines)).await().also {
            if (BuildConfig.DEBUG) Log.d(TAG, "langue des paroles : $it")
        }

    /** Traduction anglais → français ligne par ligne, sur le téléphone (secours sans réseau). */
    suspend fun translateEnglish(lines: List<String>): List<String?> {
        translator.downloadModelIfNeeded(conditions).await()
        return LineTranslation.translateDistinct(lines) { translator.translate(it).await() }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

    private companion object {
        const val TAG = "KaraokeDrive"
    }
}
