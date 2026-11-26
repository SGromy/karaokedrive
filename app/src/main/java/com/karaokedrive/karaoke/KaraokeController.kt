package com.karaokedrive.karaoke

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.karaokedrive.BuildConfig
import com.karaokedrive.artwork.CoverArtClient
import com.karaokedrive.artwork.CoverCache
import com.karaokedrive.artwork.CoverRepository
import com.karaokedrive.audio.AudioSampler
import com.karaokedrive.favorites.Favorite
import com.karaokedrive.favorites.FavoritesRepository
import com.karaokedrive.lyrics.KugouClient
import com.karaokedrive.lyrics.LrcLibClient
import com.karaokedrive.lyrics.LrcParser
import com.karaokedrive.lyrics.Lyrics
import com.karaokedrive.lyrics.LyricsCache
import com.karaokedrive.lyrics.LyricsFinder
import com.karaokedrive.lyrics.LyricsRepository
import com.karaokedrive.lyrics.LyricsResult
import com.karaokedrive.lyrics.SyncedLyrics
import com.karaokedrive.media.MediaWatcher
import com.karaokedrive.media.NowPlaying
import com.karaokedrive.recognition.AcrCloudRecognizer
import com.karaokedrive.recognition.FallbackRecognizer
import com.karaokedrive.recognition.RecognitionResult
import com.karaokedrive.recognition.SongRecognizer
import com.karaokedrive.recognition.shazam.ShazamRecognizer
import com.karaokedrive.sync.SyncEngine
import com.karaokedrive.translate.LlmTranslator
import com.karaokedrive.translate.LyricsTranslator
import com.karaokedrive.translate.TranslationCache
import com.karaokedrive.translate.TranslationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Chef d'orchestre partagé entre l'écran du téléphone et l'écran Android Auto : il relie
 * l'écoute ([ListeningLoop]), les paroles ([LyricsRepository]), les pochettes, les favoris,
 * l'entraînement et les réglages, et publie l'état affiché ([state]).
 */
object KaraokeController {

    private const val TAG = "KaraokeDrive"
    private const val SAMPLE_MS = 8_000L
    private const val CALIBRATION_STEP_MS = 100L
    /** Durée ajoutée après la dernière ligne quand la durée du morceau est inconnue. */
    private const val FAVORITE_TAIL_MS = 5_000L
    private const val PRACTICE_TICK_MS = 300L
    /** Durée fictive quand elle est inconnue : la dernière ligne garde sa durée plafonnée. */
    private const val UNKNOWN_DURATION_MS = 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    /** Shazam (gratuit, non officiel) d'abord ; ACRCloud (payant) seulement s'il est indisponible. */
    private val recognizer: SongRecognizer = FallbackRecognizer(
        primary = ShazamRecognizer(http),
        fallback = AcrCloudRecognizer(BuildConfig.ACR_HOST, BuildConfig.ACR_ACCESS_KEY, BuildConfig.ACR_ACCESS_SECRET, http),
    )
    /** LRCLIB (lignes) et Kugou (mot par mot), interrogés ensemble. */
    private val lyricsFinder = LyricsFinder(LrcLibClient(http)::findSynced, KugouClient(http)::find, log = ::debug)
    private val coverClient = CoverArtClient(http)
    private val translator by lazy { LyricsTranslator() }
    /** Traduction par IA (Gemini par défaut) ; sans clé, seule la traduction du téléphone sert. */
    private val llm = LlmTranslator(http, BuildConfig.LLM_BASE_URL, BuildConfig.LLM_API_KEY, BuildConfig.LLM_MODEL)
    private var translationService: TranslationService? = null
    private val sampler = AudioSampler()

    val sync = SyncEngine()
    private val track = CurrentTrack()
    private val _state = MutableStateFlow(KaraokeUiState())
    val state: StateFlow<KaraokeUiState> = _state.asStateFlow()
    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()
    /** Messages ponctuels à montrer à l'utilisateur (échec d'enregistrement d'un favori…). */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: SharedFlow<String> = _notices.asSharedFlow()
    /** Une seule opération sur les favoris à la fois, hors du fil de l'interface. */
    private val favoritesLock = Mutex()

    // Caches branchés au démarrage (init), quand le dossier de l'app est connu
    private var lyricsRepo = LyricsRepository(lyricsFinder::find, cache = null)
    private var coverRepo = CoverRepository(coverClient, cache = null)
    private var favoritesRepo: FavoritesRepository? = null
    private var settings: KaraokeSettings? = null

    private var job: Job? = null
    /** Recherche des paroles du morceau en cours, qui peut se poursuivre après leur affichage. */
    private var lyricsJob: Job? = null
    private var mediaCollector: Job? = null
    private var lastMediaKey: String? = null
    private var practiceClock: PracticeClock? = null
    private var practiceWatcher: Job? = null

    private val listening = ListeningLoop(
        record = { sampler.record(SAMPLE_MS) },
        recognizer = recognizer,
        sync = sync,
        track = track,
        host = object : ListeningHost {
            override suspend fun onNewTrack(match: RecognitionResult.Match): Long? {
                showSearching(match.title, match.artist)
                track.key?.let { loadCover(it, match.title, match.artist, match.coverUrl) }
                return loadLyrics(match.title, match.artist, match.durationMs)
            }
            override fun hasLyrics() = _state.value.lyrics != null
            override fun lyricsEndMs(): Long? = _state.value.lyrics?.lines?.lastOrNull()?.endMs
            override fun onNoMatch() {
                if (_state.value.status != Status.SINGING) _state.update { it.copy(status = Status.NO_MATCH) }
            }
            override fun onSongOver() {
                // Paroles terminées et pub / animateur à l'antenne : on quitte le morceau fini
                _state.update {
                    it.copy(
                        status = Status.LISTENING, lyrics = null, trackLabel = null, message = null,
                        cover = null, translations = null, isFavorite = false,
                    )
                }
            }
            override fun onFailure(message: String) = fail(message)
        },
        clockMs = SystemClock::elapsedRealtime,
        log = ::debug,
    )

    /** À appeler au démarrage (téléphone ou voiture) : recharge les réglages et branche les caches. */
    fun init(context: Context) {
        if (settings != null) return
        val app = context.applicationContext
        val loaded = KaraokeSettings(app).also { settings = it }
        sync.calibrationMs = loaded.calibrationMs
        _state.update {
            it.copy(
                calibrationMs = loaded.calibrationMs, showBall = loaded.showBall,
                showEffects = loaded.showEffects, showTranslation = loaded.showTranslation,
            )
        }
        lyricsRepo = LyricsRepository(lyricsFinder::find, LyricsCache(File(app.cacheDir, "lyrics")))
        coverRepo = CoverRepository(coverClient, CoverCache(File(app.cacheDir, "covers")))
        favoritesRepo = FavoritesRepository(File(app.filesDir, "favorites")).also { repo ->
            onFavoritesDisk { _favorites.value = repo.list() }
        }
        mediaCollector = scope.launch { MediaWatcher.nowPlaying.collect(::onNowPlaying) }
        // Modèle de traduction téléchargé d'avance, pour être prêt hors connexion en voiture
        runCatching { translator.prepare() }
        translationService = TranslationService(
            detectLanguage = translator::detectLanguage,
            ai = llm.takeIf { it.isConfigured }?.let { it::translate },
            offline = translator::translateEnglish,
            cache = TranslationCache(File(app.cacheDir, "translations")),
        )
    }

    val canRecognize: Boolean get() = recognizer.isConfigured

    /** Le décalage ne sert qu'au micro : la position d'une application est déjà exacte. */
    fun positionMs(): Long? = practiceClock?.positionAt(SystemClock.elapsedRealtime())
        ?: if (_state.value.followsApp) rawPositionMs() else sync.positionMs()

    // --- Écoute ------------------------------------------------------------------------------

    /** Renvoie false si l'écoute n'a pas pu démarrer (aucun service de reconnaissance). */
    fun startListening(): Boolean {
        stop()
        appWithPosition()?.let { followApp(it); return true }
        if (!recognizer.isConfigured) {
            _state.update {
                it.copy(status = Status.ERROR, message = "Ajoutez vos clés ACRCloud dans local.properties, puis recompilez l'app.")
            }
            return false
        }
        launchListeningLoop()
        return true
    }

    /**
     * La musique a changé : on oublie le morceau en cours et on relance tout de suite
     * une identification, sans repasser par l'écran d'accueil.
     */
    fun rescan() {
        if (practiceClock != null) return
        if (!recognizer.isConfigured) {
            startListening()
            return
        }
        job?.cancel()
        lyricsJob?.cancel()
        track.key = null
        sync.reset()
        appWithPosition()?.let { followApp(it); return }
        _state.update { it.copy(status = Status.LISTENING, lyrics = null, trackLabel = null, message = null, followsApp = false) }
        launchListeningLoop()
    }

    /** [announce] = afficher « J'écoute… » (faux quand on revérifie en gardant les paroles affichées). */
    private fun launchListeningLoop(announce: Boolean = true) {
        job = scope.launch {
            // L'appli média a pu s'annoncer avant le démarrage de l'écoute : on la relit ici
            val app = MediaWatcher.nowPlaying.value?.appLabel
            if (announce) _state.update { it.copy(status = Status.LISTENING, message = null, sourceLabel = app) }
            listening.run()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        lyricsJob?.cancel()
        track.clear()
        lastMediaKey = null
        listening.reset()
        practiceClock = null
        practiceWatcher?.cancel()
        sync.reset()
        _state.update { it.reset() }
    }

    /**
     * Relance tout de suite une reconnaissance en gardant l'affichage : si le morceau a
     * vraiment changé, les nouvelles paroles arrivent ; sinon rien ne bouge à l'écran.
     */
    private fun recheckNow() {
        if (_state.value.followsApp) return
        job?.cancel()
        launchListeningLoop(announce = _state.value.status != Status.SINGING)
    }

    // --- Paroles, pochette, traduction ------------------------------------------------------

    /**
     * Charge et affiche les paroles du morceau. Revient dès que des paroles sont à l'écran :
     * ligne par ligne si le mot par mot se fait attendre, qui les remplacera à son arrivée.
     * Renvoie la durée du morceau retenue (celle de la reconnaissance, sinon celle des
     * paroles ; 0 si inconnue), ou null s'il n'y a pas de paroles.
     */
    private suspend fun loadLyrics(title: String, artist: String, durationMs: Long): Long? {
        val key = TrackKey.of(title, artist)
        val shown = CompletableDeferred<Long?>()
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            try {
                val result = lyricsRepo.load(title, artist, durationMs) { preview ->
                    if (!shown.isCompleted) showLyrics(key, title, artist, preview, durationMs)?.let(shown::complete)
                }
                val synced = (result as? LyricsResult.Found)?.lyrics
                debug("paroles=${synced != null} (${(result as? LyricsResult.Found)?.origin}) motàmot=${synced?.lrc?.contains('<') == true} aperçu=${shown.isCompleted}")
                when {
                    !shown.isCompleted -> shown.complete(synced?.let { showLyrics(key, title, artist, it, durationMs) } ?: showNoLyrics(key, title, artist, result))
                    synced != null && synced.lrc != track.lrc && track.key == key -> {
                        debug("mot à mot arrivé après les lignes : remplacement")
                        showLyrics(key, title, artist, synced, durationMs)
                    }
                }
            } catch (e: CancellationException) {
                shown.cancel()
                throw e
            } catch (e: Exception) {
                // Avant l'affichage, l'erreur remonte à l'appelant ; après, seul le remplacement est perdu
                if (!shown.completeExceptionally(e)) Log.w(TAG, "remplacement des paroles impossible : ${e.message}")
            }
        }
        return shown.await()
    }

    /**
     * Nouveau morceau : efface tout ce qui concernait le précédent (paroles, traduction, favori,
     * pochette) et annonce la recherche de ses paroles (état SINGING sans paroles).
     */
    private fun showSearching(title: String, artist: String) {
        track.lrc = null
        _state.update {
            it.copy(
                status = Status.SINGING, lyrics = null, translations = null, isFavorite = false,
                cover = null, trackLabel = "$title — $artist", message = null,
            )
        }
    }

    /**
     * Affiche [synced] si le morceau [key] est toujours en cours. Renvoie la durée retenue,
     * ou null si rien n'est affichable (paroles vides, morceau déjà passé).
     */
    private fun showLyrics(key: String, title: String, artist: String, synced: SyncedLyrics, durationMs: Long): Long? {
        val duration = if (durationMs > 0) durationMs else synced.durationMs
        val lines = LrcParser.parse(synced.lrc, duration.takeIf { it > 0 } ?: UNKNOWN_DURATION_MS)
        if (lines.isEmpty() || track.key != key) return null
        track.lrc = synced.lrc
        val isFavorite = favoritesRepo?.contains(favoriteId(title, artist)) == true
        val texts = lines.map { it.text }
        // Mot par mot remplaçant les lignes : même texte, la traduction affichée reste valable
        val sameText = _state.value.lyrics?.lines?.map { it.text } == texts
        _state.update {
            it.copy(
                status = Status.SINGING, lyrics = Lyrics(title, artist, lines), trackLabel = "$title — $artist",
                message = null, translations = if (sameText) it.translations else null, isFavorite = isFavorite,
            )
        }
        if (!sameText) translateLyrics(key, texts)
        return duration
    }

    private fun showNoLyrics(key: String, title: String, artist: String, result: LyricsResult): Long? {
        if (track.key != key) return null
        val message = if (result == LyricsResult.Unreachable) "Service de paroles injoignable, nouvel essai au prochain morceau" else null
        _state.update { it.copy(status = Status.NO_LYRICS, lyrics = null, trackLabel = "$title — $artist", message = message) }
        return null
    }

    /** Charge la pochette sans retarder les paroles ; ignorée si le morceau a changé entre-temps. */
    private fun loadCover(key: String, title: String, artist: String, knownUrl: String?) {
        _state.update { it.copy(cover = null) }
        scope.launch {
            val cover = coverRepo.load(title, artist, knownUrl)
            debug("pochette=${cover.bitmap != null} (${cover.origin})")
            if (track.key == key) _state.update { it.copy(cover = cover.bitmap) }
        }
    }

    /**
     * Traduit les paroles en français en arrière-plan (IA, sinon téléphone) ; ignoré si le
     * morceau a changé entre-temps.
     */
    private fun translateLyrics(key: String, lines: List<String>) {
        if (!_state.value.showTranslation) return // réglage coupé : pas de calcul inutile
        val service = translationService ?: return
        scope.launch {
            val translated = try {
                service.translate(TrackKey.fileStem(key), lines)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "traduction impossible : ${e.message}")
                null
            }
            debug("traduction : ${translated?.lines?.count { it != null } ?: 0} lignes (${translated?.origin})")
            // Ignorée si le morceau ou le texte affiché a changé entre-temps (lignes remplacées par le mot par mot)
            val stillShown = track.key == key && _state.value.lyrics?.lines?.map { it.text } == lines
            if (translated != null && stillShown && _state.value.showTranslation) {
                _state.update { it.copy(translations = translated.lines) }
            }
        }
    }

    // --- Application média du téléphone -----------------------------------------------------

    /** Réagit à ce que publie l'application média du téléphone. */
    private fun onNowPlaying(nowPlaying: NowPlaying?) {
        val state = _state.value
        val isActive = state.status != Status.IDLE && !state.isPractice
        val decision = MediaPolicy.decide(nowPlaying, lastMediaKey, track.key, isActive)
        lastMediaKey = nowPlaying?.let { TrackKey.of(it.title, it.artist) }
        if (isActive && !state.followsApp) _state.update { it.copy(sourceLabel = nowPlaying?.appLabel) }
        when (decision) {
            MediaDecision.Ignore -> Unit
            MediaDecision.Rescan -> {
                debug("changement annoncé par ${nowPlaying?.appLabel} : vérification")
                recheckNow()
            }
            is MediaDecision.FollowApp -> followApp(decision.nowPlaying)
        }
    }

    private fun appWithPosition(): NowPlaying? = MediaWatcher.nowPlaying.value?.takeIf { it.hasPosition }

    /** Synchro exacte sur la position publiée par l'application (Spotify, Deezer…), sans micro. */
    private fun followApp(nowPlaying: NowPlaying) {
        val key = TrackKey.of(nowPlaying.title, nowPlaying.artist)
        val anchorExactly = {
            val now = SystemClock.elapsedRealtime()
            sync.reset()
            sync.anchor(nowPlaying.positionAt(now), now)
        }
        if (key == track.key && _state.value.followsApp) {
            anchorExactly() // simple mise à jour de position (avance, retour arrière…)
            return
        }
        job?.cancel()
        lyricsJob?.cancel()
        track.key = key
        track.durationMs = nowPlaying.durationMs
        sync.reset() // plus de position de l'ancien morceau
        showSearching(nowPlaying.title, nowPlaying.artist)
        _state.update { it.copy(followsApp = true, sourceLabel = nowPlaying.appLabel) }
        debug("suivi de ${nowPlaying.appLabel} : ${nowPlaying.title} — ${nowPlaying.artist}")
        loadCover(key, nowPlaying.title, nowPlaying.artist, knownUrl = null)
        job = scope.launch {
            loadLyrics(nowPlaying.title, nowPlaying.artist, nowPlaying.durationMs)
            anchorExactly()
        }
    }

    // --- Favoris -----------------------------------------------------------------------------

    /** Ajoute le morceau affiché aux favoris, ou l'en retire. */
    fun toggleFavorite() {
        val repo = favoritesRepo ?: return
        val state = _state.value
        val lyrics = state.lyrics ?: return
        val id = favoriteId(lyrics.title, lyrics.artist)
        val lrc = track.lrc
        val duration = track.durationMs.takeIf { it > 0 } ?: (lyrics.lines.last().endMs + FAVORITE_TAIL_MS)
        onFavoritesDisk {
            if (repo.contains(id)) {
                if (!repo.delete(id)) notify("Impossible de retirer ce favori")
            } else if (lrc != null) {
                val favorite = Favorite(id, lyrics.title, lyrics.artist, duration, lrc, System.currentTimeMillis())
                if (!repo.save(favorite, state.cover)) notify("Favori non enregistré : stockage du téléphone indisponible")
            }
            refreshFavorites(repo, id)
        }
    }

    fun deleteFavorite(id: String) {
        val repo = favoritesRepo ?: return
        onFavoritesDisk {
            if (!repo.delete(id)) notify("Impossible de supprimer ce favori")
            refreshFavorites(repo, id)
        }
    }

    /** Pochette d'un favori (lecture et décodage sur disque : hors du fil de l'interface). */
    fun favoriteCover(id: String): Bitmap? = favoritesRepo?.cover(id)

    /** Liste des favoris, et cœur du morceau affiché s'il s'agit de [id]. */
    private fun refreshFavorites(repo: FavoritesRepository, id: String) {
        _favorites.value = repo.list()
        val isFavorite = repo.contains(id)
        _state.update { current ->
            val shownId = current.lyrics?.let { favoriteId(it.title, it.artist) }
            if (shownId == id) current.copy(isFavorite = isFavorite) else current
        }
    }

    /** Opération sur les favoris, sur le disque et une à la fois ; une erreur est signalée, jamais fatale. */
    private fun onFavoritesDisk(action: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) {
            favoritesLock.withLock {
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "favoris : ${e.message}", e)
                    notify("Favoris momentanément indisponibles")
                }
            }
        }
    }

    private fun notify(message: String) {
        _notices.tryEmit(message)
    }

    private fun favoriteId(title: String, artist: String): String = TrackKey.fileName(title, artist).removeSuffix(".lrc")

    // --- Mode entraînement (paroles sans musique) -------------------------------------------

    fun startPractice(id: String) {
        val repo = favoritesRepo ?: return
        onFavoritesDisk {
            val favorite = repo.find(id)
            if (favorite == null) notify("Ce favori est illisible") else beginPractice(favorite, repo.cover(id))
        }
    }

    private fun beginPractice(favorite: Favorite, cover: Bitmap?) {
        stop()
        val lines = LrcParser.parse(favorite.lrc, favorite.durationMs.takeIf { it > 0 } ?: UNKNOWN_DURATION_MS)
        if (lines.isEmpty()) {
            notify("Ce favori ne contient pas de paroles lisibles")
            return
        }
        val duration = favorite.durationMs.takeIf { it > 0 } ?: (lines.last().endMs + FAVORITE_TAIL_MS)
        val key = TrackKey.of(favorite.title, favorite.artist)
        track.key = key
        track.lrc = favorite.lrc
        track.durationMs = duration
        practiceClock = PracticeClock(durationMs = duration).play(SystemClock.elapsedRealtime())
        _state.update {
            it.copy(
                status = Status.SINGING, lyrics = Lyrics(favorite.title, favorite.artist, lines),
                trackLabel = "${favorite.title} — ${favorite.artist}", cover = cover,
                isFavorite = true, isPractice = true, practicePlaying = true, practiceSpeed = 1f,
            )
        }
        translateLyrics(key, lines.map { it.text })
        // Met la lecture en pause à la fin du morceau (bouton lecture à jour)
        practiceWatcher = scope.launch {
            while (isActive) {
                val clock = practiceClock ?: break
                if (clock.isPlaying && clock.isFinishedAt(SystemClock.elapsedRealtime())) updatePractice { it.pause(SystemClock.elapsedRealtime()) }
                delay(PRACTICE_TICK_MS)
            }
        }
    }

    fun practiceTogglePlay() = updatePractice { clock ->
        val now = SystemClock.elapsedRealtime()
        if (clock.isPlaying) clock.pause(now) else clock.play(now)
    }

    fun practiceSeek(deltaMs: Long) = updatePractice { it.seekBy(deltaMs, SystemClock.elapsedRealtime()) }

    fun practiceRestart() = updatePractice { it.restart(SystemClock.elapsedRealtime()) }

    fun practiceNextSpeed() = updatePractice { it.nextSpeed(SystemClock.elapsedRealtime()) }

    private fun updatePractice(change: (PracticeClock) -> PracticeClock) {
        val clock = practiceClock?.let(change) ?: return
        practiceClock = clock
        _state.update { it.copy(practicePlaying = clock.isPlaying, practiceSpeed = clock.speed) }
    }

    // --- Réglages ----------------------------------------------------------------------------

    /** Affiche ou masque la boule ; le remplissage des mots reste actif. */
    fun toggleBall() {
        val show = !_state.value.showBall
        settings?.showBall = show
        _state.update { it.copy(showBall = show) }
    }

    /** Active ou coupe l'onde et le visualiseur animés (téléphone). */
    fun toggleEffects() {
        val show = !_state.value.showEffects
        settings?.showEffects = show
        _state.update { it.copy(showEffects = show) }
    }

    /**
     * Active ou coupe la traduction automatique. Réactivée en cours de morceau, elle traduit
     * aussitôt les paroles affichées ; coupée, elle disparaît de l'écran tout de suite.
     */
    fun toggleTranslation() {
        val show = !_state.value.showTranslation
        settings?.showTranslation = show
        _state.update { it.copy(showTranslation = show, translations = null) }
        val lyrics = _state.value.lyrics
        val key = track.key
        if (show && lyrics != null && key != null) translateLyrics(key, lyrics.lines.map { it.text })
    }

    fun nudgeCalibration(steps: Int) {
        val value = sync.calibrationMs + steps * CALIBRATION_STEP_MS
        sync.calibrationMs = value
        settings?.calibrationMs = value
        _state.update { it.copy(calibrationMs = value) }
    }

    // --- Divers ------------------------------------------------------------------------------

    /** Position dans le repère de la reconnaissance (sans le réglage de décalage). */
    private fun rawPositionMs(): Long? = sync.positionMs()?.minus(sync.calibrationMs)

    private fun fail(message: String) {
        // Paroles affichées : on chante toujours, l'erreur attendra (pas pendant la recherche des paroles)
        _state.update { if (it.status == Status.SINGING && it.lyrics != null) it else it.copy(status = Status.ERROR, message = message) }
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
