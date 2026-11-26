package com.karaokedrive.translate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** D'où vient la traduction affichée (diagnostic). */
enum class TranslationOrigin { CACHE, AI, OFFLINE }

data class Translated(val lines: List<String?>, val origin: TranslationOrigin)

/**
 * Traduction française des paroles : cache, sinon IA (toute la chanson d'un coup, n'importe
 * quelle langue), sinon traduction du téléphone en secours (anglais seulement, sans réseau).
 * Les chansons en français ne sont pas traduites.
 *
 * @param ai traduction par IA (null sans clé) ; lève une exception réseau ou renvoie null si inutilisable
 */
class TranslationService(
    private val detectLanguage: suspend (List<String>) -> String,
    private val ai: (suspend (lines: List<String>, language: String) -> List<String?>?)?,
    private val offline: suspend (List<String>) -> List<String?>,
    private val cache: TranslationCache?,
) {

    /** null si rien à traduire (français, langue inconnue) ou si aucune traduction n'est possible. */
    suspend fun translate(key: String, lines: List<String>): Translated? {
        withContext(Dispatchers.IO) { cache?.get(key, lines) }?.let { return Translated(it, TranslationOrigin.CACHE) }
        val language = detectLanguage(lines)
        if (language == FRENCH || language == UNKNOWN) return null
        aiTranslation(lines, language)?.let { translated ->
            withContext(Dispatchers.IO) { cache?.put(key, lines, translated) }
            return Translated(translated, TranslationOrigin.AI)
        }
        return if (language == ENGLISH) Translated(offline(lines), TranslationOrigin.OFFLINE) else null
    }

    private suspend fun aiTranslation(lines: List<String>, language: String): List<String?>? = try {
        ai?.invoke(lines, language)?.takeIf { it.size == lines.size }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null // réseau absent, quota dépassé… : traduction de secours
    }

    private companion object {
        const val FRENCH = "fr"
        const val ENGLISH = "en"
        /** Code renvoyé par la détection de langue quand elle ne sait pas. */
        const val UNKNOWN = "und"
    }
}
