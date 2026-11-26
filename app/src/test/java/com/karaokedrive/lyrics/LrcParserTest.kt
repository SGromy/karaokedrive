package com.karaokedrive.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parse les lignes simples et termine chaque ligne au début de la suivante`() {
        val lrc = """
            [00:01.00]Sur la route du soir
            [00:04.50]On chante tous ensemble
        """.trimIndent()

        val lines = LrcParser.parse(lrc, trackDurationMs = 10_000)

        assertEquals(2, lines.size)
        assertEquals(1_000, lines[0].startMs)
        assertEquals(4_500, lines[0].endMs)
        assertEquals(4_500, lines[1].startMs)
        assertEquals("Sur la route du soir", lines[0].text)
    }

    @Test
    fun `répartit les mots selon leurs syllabes sans chevauchement`() {
        val lines = LrcParser.parse("[00:00.00]la maison\n[00:02.00]fin", trackDurationMs = 8_000)
        val words = lines[0].words

        assertEquals(listOf("la", "maison"), words.map { it.text })
        assertEquals(0L, words[0].startMs)
        assertEquals(words[0].endMs, words[1].startMs)
        // « maison » a deux syllabes, « la » une seule → deux fois plus long
        assertEquals(2.0, (words[1].endMs - words[1].startMs).toDouble() / (words[0].endMs - words[0].startMs), 0.05)
    }

    @Test
    fun `une phrase courte suivie d'une pause ne s'étire pas sur toute la pause`() {
        val lines = LrcParser.parse("[00:00.00]oh oui\n[00:10.00]suite", trackDurationMs = 20_000)

        // 2 syllabes chantées à débit réaliste, puis la boule attend la ligne suivante
        assertEquals(2 * LrcParser.MAX_SYLLABLE_MS, lines[0].words.last().endMs)
        assertEquals(LrcParser.MAX_LINE_MS, lines[0].endMs)
    }

    @Test
    fun `compte les syllabes par groupes de voyelles, au moins une par mot`() {
        assertEquals(1, LrcParser.syllables("la"))
        assertEquals(2, LrcParser.syllables("maison"))
        assertEquals(3, LrcParser.syllables("kilomètres"))
        assertEquals(1, LrcParser.syllables("pff"))
    }

    @Test
    fun `utilise les balises de mots du format LRC enrichi`() {
        val lrc = "[00:02.00]<00:02.00>Ho <00:02.80>hé <00:03.50>les amis\n[00:05.00]suite"

        val words = LrcParser.parse(lrc, trackDurationMs = 9_000)[0].words

        assertEquals(listOf("Ho", "hé", "les", "amis"), words.map { it.text })
        assertEquals(2_000L, words[0].startMs)
        assertEquals(2_800L, words[1].startMs)
        assertEquals(3_500L, words[2].startMs)
        assertEquals(5_000L, words.last().endMs)
    }

    @Test
    fun `ignore les métadonnées, les lignes vides et trie par temps`() {
        val lrc = "[ar:Quelqu'un]\n[00:05.00]deux\n[00:03.00]\n[00:01.00]un"

        val lines = LrcParser.parse(lrc, trackDurationMs = 7_000)

        assertEquals(listOf("un", "deux"), lines.map { it.text })
    }

    @Test
    fun `plafonne la dernière ligne pour ne pas s'étirer jusqu'à la fin du morceau`() {
        val lines = LrcParser.parse("[00:01.00]solo", trackDurationMs = 300_000)

        assertEquals(1_000 + LrcParser.MAX_LINE_MS, lines[0].endMs)
    }

    @Test
    fun `accepte les centièmes et les millièmes`() {
        val lines = LrcParser.parse("[01:02.5]a\n[01:03.250]b", trackDurationMs = 70_000)

        assertEquals(62_500L, lines[0].startMs)
        assertEquals(63_250L, lines[1].startMs)
    }
}
