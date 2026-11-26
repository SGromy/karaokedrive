package com.karaokedrive.lyrics

/** Un mot chanté, avec sa fenêtre temporelle dans le morceau (en ms). */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/** Une ligne de paroles ; [words] est toujours non vide et trié dans le temps. */
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val words: List<LyricWord>,
) {
    val text: String get() = words.joinToString(" ") { it.text }
}

data class Lyrics(
    val title: String,
    val artist: String,
    val lines: List<LyricLine>,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}
