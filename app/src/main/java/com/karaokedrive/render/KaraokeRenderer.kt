package com.karaokedrive.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import com.karaokedrive.karaoke.KaraokeFrame
import com.karaokedrive.lyrics.LyricLine
import com.karaokedrive.lyrics.Lyrics
import kotlin.math.abs
import kotlin.math.min

/**
 * Dessine la scène karaoké sur un [Canvas] Android classique : le même code sert
 * au téléphone (Compose → nativeCanvas) et à l'écran central (surface Android Auto).
 */
class KaraokeRenderer(
    /** Badge du logo, affiché sur les écrans d'attente. */
    val logo: Bitmap? = null,
    /** Police de la traduction (Nunito), à défaut une italique système. */
    translationTypeface: Typeface? = null,
) {

    private data class WordBox(val x: Float, val baseline: Float, val width: Float)
    private data class LineLayout(val size: Float, val boxes: List<WordBox>, val top: Float, val bottom: Float)
    private data class LayoutKey(val line: LyricLine, val area: RectF)
    /**
     * Effets animés d'une image : onde (activée ou non), visualiseur en barres (paysage seulement :
     * en portrait la pochette occupe déjà le haut), couleurs de la pochette, pulsation, horloge.
     */
    private data class Effects(val enabled: Boolean, val spectrum: Boolean, val colors: Pair<Int, Int>, val energy: Float, val timeMs: Long)

    private val heavy = Typeface.create(Typeface.DEFAULT, 800, false)
    private val medium = Typeface.create(Typeface.DEFAULT, 500, false)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = heavy }
    private val contextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = medium; textAlign = Paint.Align.CENTER }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = KaraokeTheme.shadow; alpha = 90 }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = medium; color = KaraokeTheme.dim }
    private val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = medium; color = KaraokeTheme.dim; alpha = 170 }
    private val translationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = translationTypeface ?: Typeface.create(Typeface.DEFAULT, 500, true)
        textAlign = Paint.Align.CENTER
        color = KaraokeTheme.translation
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    /** Place des paroles à droite du panneau latéral (écrans très larges). */
    private val lyricsArea = RectF()

    private var cachedKey: LayoutKey? = null
    private var cachedLayout: LineLayout? = null
    private var backgroundKey: RectF? = null
    private val coverPainter = CoverArtPainter()
    private val wavePainter = NeonWavePainter()
    private val spectrumPainter = SpectrumPainter()
    private val accents = AccentColors()
    private val spectrumBounds = RectF()
    private val scrimPaint = Paint()
    private val coverBounds = RectF()
    /** Abscisse du texte d'en-tête (décalée quand la vignette est affichée). */
    private var headerTextX = 0f

    /**
     * Zone du fond de scène si elle dépasse celle des paroles : sur le téléphone, le fond
     * coloré continue sous la barre de réglages (effet vitre polie), pas les paroles.
     */
    var backdropArea: RectF? = null

    /** Limite basse du texte (haut de la barre de réglages du téléphone), si elle est plus haute que [area]. */
    var contentBottom: Float? = null

    /** Largeur réservée à droite de l'en-tête (cœur des favoris sur le téléphone), en pixels. */
    var headerRightInset: Float = 0f

    /** [area] = zone réellement visible (sur Android Auto, hors bandeaux du système). */
    fun draw(
        canvas: Canvas, area: RectF, lyrics: Lyrics, frame: KaraokeFrame,
        source: String? = null, cover: Bitmap? = null,
        translations: List<String?>? = null,
        showBall: Boolean = true,
        effects: Boolean = false,
    ) {
        drawStage(canvas, area)
        cover?.let { coverPainter.drawBackdrop(canvas, backdropArea ?: area, it) }
        val portrait = area.height() > area.width() * PORTRAIT_RATIO
        val fx = Effects(effects, effects && !portrait, accents.of(cover ?: logo), waveEnergy(frame), SystemClock.uptimeMillis())
        // En portrait, la pochette s'affiche en grand au-dessus des paroles (pas de vignette en double)
        val largeCover = cover?.takeIf { portrait }
        // Écran très large (téléphone en paysage) : pochette et titre en grand à gauche,
        // les paroles se centrent dans la place restante
        val wide = area.width() > area.height() * WIDE_RATIO
        if (wide) {
            drawSidePanel(canvas, area, lyrics, cover ?: logo, source, fx)
        } else {
            drawHeader(canvas, area, "${lyrics.title}  ·  ${lyrics.artist}", cover.takeIf { largeCover == null })
            source?.let { drawSource(canvas, area, it) }
        }
        val stage = if (wide) lyricsArea else area
        if (fx.spectrum) drawSpectrum(canvas, stage, fx)
        drawLyrics(canvas, stage, lyrics, frame, largeCover, translations, showBall, fx)
    }

    /**
     * Visualiseur en barres, flouté derrière les paroles (paysage). La ligne miroir suit le
     * centre des paroles mais reste fixe d'une ligne à l'autre, pour que les barres ne sautent pas.
     */
    private fun drawSpectrum(canvas: Canvas, stage: RectF, fx: Effects) {
        val centerY = stage.centerY() + stage.height() * 0.04f
        val half = stage.height() * 0.42f
        spectrumBounds.set(stage.left, centerY - half, stage.right, centerY + half)
        spectrumPainter.draw(canvas, spectrumBounds, fx.energy, fx.colors, fx.timeMs)
    }

    /** Bande sombre et fondue derrière la ligne chantée : elle reste lisible sur le visualiseur. */
    private fun drawScrim(canvas: Canvas, area: RectF, layout: LineLayout, bottom: Float) {
        val top = layout.top - layout.size * 0.5f
        val fade = layout.size * 0.8f
        scrimPaint.shader = LinearGradient(
            0f, top - fade, 0f, bottom + fade, intArrayOf(0, SCRIM_COLOR, SCRIM_COLOR, 0),
            floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(area.left, top - fade, area.right, bottom + fade, scrimPaint)
    }

    private fun drawLyrics(
        canvas: Canvas, area: RectF, lyrics: Lyrics, frame: KaraokeFrame,
        largeCover: Bitmap?, translations: List<String?>?, showBall: Boolean, fx: Effects,
    ) {
        val line = lyrics.lines.getOrNull(frame.lineIndex) ?: return
        val layout = layoutFor(line, area)

        val coverBottom = largeCover?.let { cover ->
            largeCoverBounds(area)?.also {
                // Onde néon derrière la pochette : elle déborde de chaque côté
                if (fx.enabled) wavePainter.draw(canvas, area.left, area.right, it.centerX(), it.centerY(), it.width() * WAVE_AMPLITUDE, fx.energy, fx.colors, fx.timeMs)
                coverPainter.drawThumbnail(canvas, it, cover)
            }?.bottom
        }
        lyrics.lines.getOrNull(frame.lineIndex - 1)?.let {
            val size = layout.size * 0.5f
            val baseline = layout.top - layout.size * 1.25f
            // Ligne très haute (3 rangées) : on sacrifie la ligne précédente plutôt que de recouvrir la pochette
            if (coverBottom == null || baseline - size > coverBottom) drawContextLine(canvas, area, it.text, size, baseline)
        }
        // Traduction française de la ligne en cours, juste en dessous ; la ligne suivante descend d'autant.
        // En portrait il y a la place : même taille que la ligne chantée, sur plusieurs lignes si besoin.
        val portrait = area.height() > area.width() * PORTRAIT_RATIO
        val translation = translations?.getOrNull(frame.lineIndex)
        // Bande sombre jusqu'au bas de la traduction (≈ 2 lignes à 70 % en paysage)
        if (fx.spectrum) drawScrim(canvas, area, layout, layout.bottom + layout.size * (if (translation != null) 1.9f else 1f))
        val nextBaseline = when {
            translation == null -> layout.bottom + layout.size * 0.95f
            portrait -> drawWrappedTranslation(canvas, area, translation, layout, layout.size, MAX_TRANSLATION_ROWS) + layout.size * 0.8f
            else -> drawWrappedTranslation(
                canvas, area, translation, layout, layout.size * LANDSCAPE_TRANSLATION_SCALE, MAX_LANDSCAPE_TRANSLATION_ROWS,
            ) + layout.size * 0.75f
        }
        // La ligne suivante n'est dessinée que si elle tient dans la zone visible
        val nextFits = nextBaseline < min(area.bottom, contentBottom ?: area.bottom) - layout.size * 0.2f
        lyrics.lines.getOrNull(frame.lineIndex + 1)?.takeIf { nextFits }?.let {
            drawContextLine(canvas, area, it.text, layout.size * 0.55f, nextBaseline)
        }
        drawWords(canvas, line, layout, frame.wordFills, if (frame.isWaiting) 0.7f else 1f)
        if (showBall) drawBall(canvas, area, layout, frame, fx.colors)
    }

    /**
     * Place de la grande pochette (portrait), calculée à partir de l'écran seul : elle ne doit
     * pas changer de taille quand la ligne en cours passe sur une ou deux rangées.
     * On réserve la place d'une ligne de paroles de taille maximale sur deux rangées.
     */
    private fun largeCoverBounds(area: RectF): RectF? {
        val headerSize = min(area.width(), area.height()) * 0.045f
        val lyricsSize = min(area.height() * 0.14f, area.width() * 0.085f)
        val lyricsTop = area.centerY() + area.height() * 0.04f - lyricsSize * 1.3f
        val top = area.top + headerSize * 4.2f
        val bottom = lyricsTop - lyricsSize * 2.2f // au-dessus de la ligne précédente et du rebond de la boule
        val side = min(area.width() * 0.66f, (bottom - top) * 0.92f)
        if (side < area.width() * 0.25f) return null // pas assez de place : on s'en passe
        val centerY = (top + bottom) / 2
        return coverBounds.apply { set(area.centerX() - side / 2, centerY - side / 2, area.centerX() + side / 2, centerY + side / 2) }
    }

    /** Fond de scène seul, teinté par [art] (pochette ou logo). */
    fun drawBackdrop(canvas: Canvas, area: RectF, art: Bitmap?) {
        drawStage(canvas, area)
        art?.let { coverPainter.drawBackdrop(canvas, backdropArea ?: area, it) }
    }

    /** Écran d'attente (écoute en cours, morceau sans paroles…), avec la pochette si connue. */
    fun drawMessage(canvas: Canvas, area: RectF, title: String, subtitle: String?, cover: Bitmap? = null) {
        drawStage(canvas, area)
        val size = min(area.width(), area.height()) * 0.09f
        cover?.let {
            coverPainter.drawBackdrop(canvas, area, it)
            val bottom = area.centerY() - size * 1.2f
            // En portrait, l'espace au-dessus du texte permet un grand visuel (~80 % de la largeur)
            val side = if (area.height() > area.width() * PORTRAIT_RATIO) {
                min(area.width() * 0.8f, (bottom - area.top) * 0.9f)
            } else {
                min(area.width(), area.height()) * 0.32f
            }
            coverBounds.set(area.centerX() - side / 2, bottom - side, area.centerX() + side / 2, bottom)
            coverPainter.drawThumbnail(canvas, coverBounds, it)
        }
        textPaint.apply { textSize = size; color = KaraokeTheme.unsung; alpha = 255; shader = null; textAlign = Paint.Align.CENTER }
        canvas.drawText(title, area.centerX(), area.centerY(), textPaint)
        textPaint.textAlign = Paint.Align.LEFT
        subtitle?.let { drawContextLine(canvas, area, it, size * 0.45f, area.centerY() + size * 1.1f) }
    }

    private fun drawStage(canvas: Canvas, visible: RectF) {
        val area = backdropArea ?: visible
        if (backgroundKey != area) {
            backgroundKey = RectF(area)
            stagePaint.shader = RadialGradient(
                area.centerX(), area.top + area.height() * 0.35f, area.width() * 0.6f,
                KaraokeTheme.spotlight, KaraokeTheme.stageBottom, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawColor(KaraokeTheme.stageBottom)
        canvas.drawRect(area, stagePaint)
    }

    /** Titre du morceau en haut à gauche, précédé de la vignette de la pochette si connue. */
    private fun drawHeader(canvas: Canvas, area: RectF, label: String, cover: Bitmap?) {
        val size = min(area.width(), area.height()) * 0.045f
        headerPaint.textSize = size
        headerTextX = area.left + size
        if (cover != null) {
            val side = size * 2.9f
            coverBounds.set(area.left + size * 0.8f, area.top + size * 0.5f, area.left + size * 0.8f + side, area.top + size * 0.5f + side)
            coverPainter.drawThumbnail(canvas, coverBounds, cover)
            headerTextX = coverBounds.right + size * 0.7f
        }
        val text = if (cover != null) label else "♪  $label"
        // Titre long : coupé avec « … » plutôt que de déborder sous le cœur et la roue dentée
        val shown = TextFit.ellipsize(text, area.right - headerTextX - size - headerRightInset, headerPaint::measureText)
        canvas.drawText(shown, headerTextX, area.top + size * 1.8f, headerPaint)
    }

    /**
     * Panneau de gauche des écrans très larges : grande pochette (ou logo), titre sur deux
     * lignes au plus, artiste et source. Laisse dans [lyricsArea] la place des paroles.
     */
    private fun drawSidePanel(canvas: Canvas, area: RectF, lyrics: Lyrics, art: Bitmap?, source: String?, fx: Effects) {
        val margin = area.height() * 0.08f
        val side = min(area.height() * SIDE_COVER_HEIGHT, area.width() * SIDE_COVER_WIDTH)
        val centerX = area.left + margin + side / 2

        panelPaint.apply { typeface = heavy; textSize = side * 0.1f }
        val titleRows = TextFit.wrapText(lyrics.title, side * 1.1f, 2, panelPaint::measureText)
        val titleSize = panelPaint.textSize
        val artistSize = side * 0.078f
        val sourceSize = side * 0.06f
        val blockHeight = side + titleSize * (0.5f + 1.2f * titleRows.size) + artistSize * 1.4f +
            (if (source != null) sourceSize * 1.6f else 0f)
        val top = maxOf(area.top + margin * 0.5f, area.centerY() - blockHeight / 2)

        val lyricsLeft = centerX + side / 2 + margin
        art?.let {
            if (fx.enabled) wavePainter.draw(canvas, area.left, lyricsLeft, centerX, top + side / 2, side * WAVE_AMPLITUDE, fx.energy, fx.colors, fx.timeMs)
            coverPainter.drawThumbnail(canvas, coverBounds.apply { set(centerX - side / 2, top, centerX + side / 2, top + side) }, it)
        }
        var baseline = top + side + titleSize * 0.5f
        panelPaint.color = KaraokeTheme.unsung
        titleRows.forEach { row ->
            baseline += titleSize * 1.2f
            canvas.drawText(row, centerX, baseline, panelPaint)
        }
        panelPaint.apply { typeface = medium; textSize = artistSize; color = KaraokeTheme.dim }
        baseline += artistSize * 1.4f
        canvas.drawText(TextFit.ellipsize(lyrics.artist, side * 1.1f, panelPaint::measureText), centerX, baseline, panelPaint)
        source?.let {
            panelPaint.apply { textSize = sourceSize; alpha = 170 }
            canvas.drawText(it, centerX, baseline + sourceSize * 1.6f, panelPaint)
        }
        lyricsArea.set(lyricsLeft, area.top, area.right, area.bottom)
    }

    /** Pulsation de l'onde : maximale quand la boule retombe sur un mot, calme entre les lignes. */
    private fun waveEnergy(frame: KaraokeFrame): Float =
        if (frame.isWaiting) IDLE_WAVE_ENERGY else 1f - frame.ball.height

    /** Source de la synchro, en petit sous le titre (« via Spotify », « micro »…). */
    private fun drawSource(canvas: Canvas, area: RectF, source: String) {
        val size = headerPaint.textSize
        sourcePaint.textSize = size * 0.72f
        canvas.drawText(source, headerTextX, area.top + size * 3.1f, sourcePaint)
    }

    /**
     * Traduction sous la ligne chantée, en [startSize] sur [maxRows] lignes au plus
     * (réduite si elle ne tient pas) : même taille que les paroles en portrait, un peu
     * plus petite en paysage.
     * @return l'ordonnée du bas du texte dessiné
     */
    private fun drawWrappedTranslation(
        canvas: Canvas, area: RectF, text: String, layout: LineLayout, startSize: Float, maxRows: Int,
    ): Float {
        val words = text.split(' ').filter(String::isNotEmpty)
        val maxWidth = area.width() * 0.88f
        var size = startSize
        var rows: List<List<Int>>
        do {
            translationPaint.textSize = size
            rows = TextFit.wrap(words, maxWidth, translationPaint.measureText(" "), translationPaint::measureText)
            if (rows.size <= maxRows) break
            size *= 0.9f
        } while (size > startSize * 0.6f)
        val rowHeight = size * 1.25f
        val firstBaseline = layout.bottom + size * TRANSLATION_GAP
        var baseline = firstBaseline
        rows.forEachIndexed { i, row ->
            baseline = firstBaseline + i * rowHeight
            canvas.drawText(row.joinToString(" ") { words[it] }, area.centerX(), baseline, translationPaint)
        }
        return baseline + size * 0.3f
    }

    private fun drawContextLine(canvas: Canvas, area: RectF, text: String, size: Float, baseline: Float) {
        contextPaint.textSize = size
        contextPaint.color = KaraokeTheme.dim
        contextPaint.alpha = KaraokeTheme.CONTEXT_ALPHA
        val shown = TextFit.ellipsize(text, area.width() * 0.9f, contextPaint::measureText)
        canvas.drawText(shown, area.centerX(), baseline, contextPaint)
    }

    private fun drawWords(canvas: Canvas, line: LyricLine, layout: LineLayout, fills: List<Float>, emphasis: Float) {
        textPaint.textSize = layout.size
        textPaint.textAlign = Paint.Align.LEFT
        fillPaint.set(textPaint)
        fillPaint.shader = LinearGradient(
            layout.boxes.first().x, 0f, layout.boxes.maxOf { it.x + it.width }, 0f,
            KaraokeTheme.sungStart, KaraokeTheme.sungEnd, Shader.TileMode.CLAMP,
        )

        line.words.forEachIndexed { i, word ->
            val box = layout.boxes[i]
            val fill = fills.getOrElse(i) { 0f }
            textPaint.shader = null
            textPaint.color = KaraokeTheme.unsung
            textPaint.alpha = (KaraokeTheme.UNSUNG_ALPHA * emphasis).toInt()
            canvas.drawText(word.text, box.x, box.baseline, textPaint)
            if (fill > 0f) {
                canvas.save()
                canvas.clipRect(box.x, box.baseline - layout.size, box.x + box.width * fill, box.baseline + layout.size * 0.4f)
                canvas.drawText(word.text, box.x, box.baseline, fillPaint)
                canvas.restore()
            }
        }
    }

    /**
     * Boule aux couleurs de la pochette : cœur clair, bord de la couleur principale, halo de la
     * seconde. Un peu plus grosse en paysage (téléphone couché, voiture), où on la regarde de loin.
     */
    private fun drawBall(canvas: Canvas, area: RectF, layout: LineLayout, frame: KaraokeFrame, colors: Pair<Int, Int>) {
        val landscape = area.width() > area.height()
        val ball = frame.ball
        val radius = layout.size * if (landscape) BALL_RADIUS_LANDSCAPE else BALL_RADIUS
        val (fromX, fromY) = landingPoint(area, layout, ball.fromWord, radius)
        val (toX, toY) = landingPoint(area, layout, ball.toWord, radius)
        val x = fromX + (toX - fromX) * ball.t
        val ground = fromY + (toY - fromY) * ball.t
        // Saut plus haut quand il est long (retour au début de la ligne suivante)
        val jump = layout.size * (BALL_JUMP + min(BALL_LONG_JUMP_BONUS, abs(toX - fromX) / (layout.size * 20f)))
        val y = ground - ball.height * jump

        // Ombre au sol qui rétrécit quand la boule monte
        val shadowW = radius * (1.2f - ball.height * 0.6f)
        canvas.drawOval(x - shadowW, ground + radius * 0.8f, x + shadowW, ground + radius * 1.15f, shadowPaint)

        // Halo + boule légèrement écrasée à l'impact
        // (seulement en mouvement : posée sur un mot ou en attente, la boule reste ronde)
        val moving = ball.fromWord != ball.toWord && ball.t > 0f
        val squash = if (moving && ball.height < 0.12f) 1f - (0.12f - ball.height) * 1.8f else 1f
        val (main, glow) = colors
        haloPaint.shader = RadialGradient(x, y, radius * 2.6f, ColorMix.withAlpha(glow, HALO_ALPHA), 0, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius * 2.6f, haloPaint)
        ballPaint.shader = RadialGradient(
            x - radius * 0.35f, y - radius * 0.35f, radius * 1.4f,
            ColorMix.withWhite(main, BALL_CORE_WHITENESS), ColorMix.withWhite(main, BALL_EDGE_WHITENESS), Shader.TileMode.CLAMP,
        )
        canvas.drawOval(x - radius / squash, y - radius * squash, x + radius / squash, y + radius * squash, ballPaint)
    }

    /**
     * Point où la boule se pose : au-dessus du centre du mot, ou en attente juste avant / après
     * la ligne, sans jamais sortir de l'écran (ligne large, premier mot collé au bord).
     */
    private fun landingPoint(area: RectF, layout: LineLayout, index: Int, radius: Float): Pair<Float, Float> {
        val boxes = layout.boxes
        val box = boxes[index.coerceIn(0, boxes.lastIndex)]
        // Posée sur le haut des lettres (assez bas pour ne pas mordre sur la rangée du dessus)
        val groundY = box.baseline - layout.size * BALL_GROUND - radius
        val margin = radius * BALL_EDGE_MARGIN
        return when {
            index < 0 -> maxOf(box.x - layout.size * 0.9f, area.left + margin) to groundY
            index > boxes.lastIndex -> minOf(box.x + box.width + layout.size * 0.5f, area.right - margin) to groundY
            else -> box.x + box.width / 2f to groundY
        }
    }

    private fun layoutFor(line: LyricLine, area: RectF): LineLayout {
        val key = LayoutKey(line, RectF(area))
        cachedLayout?.takeIf { cachedKey == key }?.let { return it }
        return computeLayout(line, area).also { cachedKey = key; cachedLayout = it }
    }

    private fun computeLayout(line: LyricLine, area: RectF): LineLayout {
        val maxWidth = area.width() * 0.88f
        val words = line.words.map { it.text }
        var size = min(area.height() * 0.14f, area.width() * 0.085f)
        val minSize = size * 0.55f
        textPaint.textSize = size
        var rows = TextFit.wrap(words, maxWidth, textPaint.measureText(" "), textPaint::measureText)
        while (rows.size > 2 && size > minSize) {
            size *= 0.9f
            textPaint.textSize = size
            rows = TextFit.wrap(words, maxWidth, textPaint.measureText(" "), textPaint::measureText)
        }

        val space = textPaint.measureText(" ")
        val rowHeight = size * 1.3f
        val blockTop = area.centerY() + area.height() * 0.04f - rows.size * rowHeight / 2f
        val boxes = rows.flatMapIndexed { r, row ->
            val widths = row.map { words[it] }.map(textPaint::measureText)
            val rowWidth = widths.sum() + space * (row.size - 1)
            var x = area.centerX() - rowWidth / 2f
            val baseline = blockTop + rowHeight * r + size
            widths.map { w -> WordBox(x, baseline, w).also { x += w + space } }
        }
        return LineLayout(size, boxes, blockTop, blockTop + rows.size * rowHeight)
    }

    private companion object {
        /** Au-delà de ce rapport hauteur / largeur, on est en portrait (téléphone debout). */
        const val PORTRAIT_RATIO = 1.3f
        /** Au-delà de ce rapport largeur / hauteur (téléphone en paysage), panneau latéral pochette + titre. */
        const val WIDE_RATIO = 2f
        /** Côté de la grande pochette du panneau latéral, en fraction de la hauteur / largeur. */
        const val SIDE_COVER_HEIGHT = 0.56f
        const val SIDE_COVER_WIDTH = 0.24f
        /** Hauteur max des pics de l'onde, en fraction du côté de la pochette (≈ jusqu'à ses bords). */
        const val WAVE_AMPLITUDE = 0.5f
        const val IDLE_WAVE_ENERGY = 0.2f
        /** Fond de scène à ~50 % d'opacité, pour la bande derrière la ligne chantée. */
        val SCRIM_COLOR = (0x80 shl 24) or (KaraokeTheme.stageBottom and 0xFFFFFF)
        const val MAX_TRANSLATION_ROWS = 3
        /** Paysage (téléphone couché, voiture) : traduction à ~70 % des paroles, sur 2 lignes au plus. */
        const val LANDSCAPE_TRANSLATION_SCALE = 0.7f
        const val MAX_LANDSCAPE_TRANSLATION_ROWS = 2
        /** Écart entre la ligne chantée et sa traduction (en tailles de police de la traduction). */
        const val TRANSLATION_GAP = 1.35f
        /** Halo de la boule à ~55 % d'opacité. */
        const val HALO_ALPHA = 0x88
        /** Rayon de la boule, en fraction de la taille des paroles. */
        const val BALL_RADIUS = 0.2f
        const val BALL_RADIUS_LANDSCAPE = 0.26f
        /** Écart minimal entre la boule en attente et le bord de l'écran, en rayons (halo compris). */
        const val BALL_EDGE_MARGIN = 2.2f
        /** Hauteur du haut des lettres au-dessus de la ligne de base, en tailles de police. */
        const val BALL_GROUND = 0.78f
        /** Hauteur d'un rebond de mot à mot, et bonus pour les longs sauts (en tailles de police). */
        const val BALL_JUMP = 0.75f
        const val BALL_LONG_JUMP_BONUS = 0.5f
        /** Boule lumineuse : cœur éclairci, bord de la couleur pure de la pochette. */
        const val BALL_CORE_WHITENESS = 0.55f
        const val BALL_EDGE_WHITENESS = 0f
    }
}
