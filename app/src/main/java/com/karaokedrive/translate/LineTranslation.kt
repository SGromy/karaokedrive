package com.karaokedrive.translate

import kotlinx.coroutines.CancellationException

/** Logique pure de traduction ligne à ligne (testable sans ML Kit). */
object LineTranslation {

    /** Longueur de l'extrait utilisé pour détecter la langue des paroles. */
    private const val DETECTION_SAMPLE_CHARS = 600

    /** Début des paroles, suffisant pour reconnaître la langue. */
    fun detectionSample(lines: List<String>): String =
        lines.filter { it.isNotBlank() }.joinToString(" ").take(DETECTION_SAMPLE_CHARS)

    /**
     * Traduit chaque ligne distincte une seule fois (les refrains se répètent) et renvoie
     * une traduction par ligne, dans le même ordre. Une ligne en échec reste sans traduction.
     */
    suspend fun translateDistinct(lines: List<String>, translate: suspend (String) -> String): List<String?> {
        val translated = mutableMapOf<String, String?>()
        for (line in lines.map(String::trim).filter(String::isNotEmpty).distinct()) {
            translated[line] = try {
                translate(line).trim().takeIf { it.isNotEmpty() && !it.equals(line, ignoreCase = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        return lines.map { translated[it.trim()] }
    }
}
