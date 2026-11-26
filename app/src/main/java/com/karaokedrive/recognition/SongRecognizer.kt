package com.karaokedrive.recognition

import android.util.Log
import com.karaokedrive.BuildConfig
import com.karaokedrive.audio.AudioSample

/** Un service capable d'identifier un extrait et de donner la position dans le morceau. */
interface SongRecognizer {
    val name: String
    val isConfigured: Boolean
    suspend fun identify(sample: AudioSample): RecognitionResult
}

/**
 * Essaie [primary] (Shazam, gratuit) puis [fallback] (ACRCloud, payant) uniquement en cas
 * d'échec technique : un « non reconnu » de Shazam n'est pas redemandé à ACRCloud,
 * pour ne pas payer une requête pendant chaque pub ou intervention d'animateur.
 */
class FallbackRecognizer(
    private val primary: SongRecognizer,
    private val fallback: SongRecognizer,
) : SongRecognizer {

    override val name: String get() = "${primary.name} (secours ${fallback.name})"
    override val isConfigured: Boolean get() = primary.isConfigured || fallback.isConfigured

    override suspend fun identify(sample: AudioSample): RecognitionResult {
        val first = if (primary.isConfigured) primary.identify(sample) else null
        if (first != null && first !is RecognitionResult.Failure) {
            if (BuildConfig.DEBUG) Log.d("KaraokeDrive", "${primary.name} : ${first::class.simpleName}")
            return first
        }
        if (!fallback.isConfigured) return first ?: RecognitionResult.Failure("Aucun service de reconnaissance")
        if (BuildConfig.DEBUG) Log.d("KaraokeDrive", "${primary.name} indisponible (${first?.message}) → ${fallback.name}")
        return fallback.identify(sample)
    }
}
