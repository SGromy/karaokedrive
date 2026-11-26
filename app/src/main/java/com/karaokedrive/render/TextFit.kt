package com.karaokedrive.render

/** Outils de mise en page du texte, indépendants d'Android pour être testables. */
object TextFit {

    /** Répartit les mots en lignes de largeur ≤ [maxWidth] ; renvoie les indices des mots par ligne. */
    fun wrap(words: List<String>, maxWidth: Float, spaceWidth: Float, measure: (String) -> Float): List<List<Int>> {
        val rows = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var width = 0f
        words.forEachIndexed { i, word ->
            val w = measure(word)
            val added = if (current.isEmpty()) w else width + spaceWidth + w
            if (current.isNotEmpty() && added > maxWidth) {
                rows += current
                current = mutableListOf(i)
                width = w
            } else {
                current += i
                width = added
            }
        }
        if (current.isNotEmpty()) rows += current
        return rows
    }

    /** Coupe [text] en [maxRows] lignes au plus ; ce qui ne tient pas finit en « … » sur la dernière. */
    fun wrapText(text: String, maxWidth: Float, maxRows: Int, measure: (String) -> Float): List<String> {
        val words = text.split(' ').filter(String::isNotEmpty)
        val rows = wrap(words, maxWidth, measure(" "), measure).map { row -> row.joinToString(" ") { words[it] } }
        val kept = if (rows.size <= maxRows) rows else rows.take(maxRows - 1) + rows.drop(maxRows - 1).joinToString(" ")
        return kept.map { ellipsize(it, maxWidth, measure) }
    }

    fun ellipsize(text: String, maxWidth: Float, measure: (String) -> Float): String {
        if (measure(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && measure(text.take(end) + "…") > maxWidth) end--
        return text.take(end).trimEnd() + "…"
    }
}
