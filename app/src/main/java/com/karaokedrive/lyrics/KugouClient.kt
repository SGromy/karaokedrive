package com.karaokedrive.lyrics

import com.karaokedrive.net.await
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer
import java.util.Base64
import kotlin.math.abs

/** Un résultat de recherche Kugou. */
data class KugouCandidate(val id: String, val accessKey: String, val song: String, val singer: String, val durationMs: Long)

/**
 * Paroles mot par mot (format KRC) depuis le service de paroles de Kugou.
 * ⚠️ API non officielle : usage personnel uniquement, comme Shazam et LRCLIB.
 */
class KugouClient(private val http: OkHttpClient) {

    /**
     * Paroles mot par mot au format LRC enrichi ; null si le morceau est introuvable ou
     * si le service ne répond pas (Kugou n'est qu'un complément de LRCLIB).
     */
    suspend fun find(title: String, artist: String, durationMs: Long): SyncedLyrics? = try {
        val (cleanTitle, mainArtist) = LyricsQuery.variants(title, artist).last()
        // 1) recherche directe dans la base de paroles ; 2) sinon, via le catalogue musical
        // (le morceau y est retrouvé, puis ses paroles par son empreinte « hash »)
        val direct = KugouMatch.best(searchLyrics("$mainArtist - $cleanTitle", hash = "", durationMs), cleanTitle, mainArtist, durationMs)
        val match = direct ?: catalogSongs(mainArtist, cleanTitle, durationMs).firstNotNullOfOrNull { song ->
            KugouMatch.best(searchLyrics(keyword = "", hash = song.id, durationMs = song.durationMs), cleanTitle, mainArtist, durationMs)
        }
        match?.let { download(it, title) }
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        null
    } catch (e: IllegalArgumentException) {
        null // contenu base64 invalide
    }

    private suspend fun searchLyrics(keyword: String, hash: String, durationMs: Long): List<KugouCandidate> {
        val url = BASE.newBuilder().addPathSegment("search")
            .addQueryParameter("ver", "1").addQueryParameter("man", "yes").addQueryParameter("client", "pc")
            .addQueryParameter("keyword", keyword)
            .apply { if (durationMs > 0) addQueryParameter("duration", durationMs.toString()) }
            .addQueryParameter("hash", hash)
            .build()
        return get(url)?.let(::parseCandidates).orEmpty()
    }

    /** Morceaux du catalogue Kugou qui correspondent (id = empreinte « hash » du fichier audio). */
    private suspend fun catalogSongs(artist: String, title: String, durationMs: Long): List<KugouCandidate> {
        val url = CATALOG.newBuilder()
            .addQueryParameter("format", "json").addQueryParameter("keyword", "$artist $title")
            .addQueryParameter("page", "1").addQueryParameter("pagesize", "5").addQueryParameter("showtype", "1")
            .build()
        val info = get(url)?.let { JSONObject(it).optJSONObject("data")?.optJSONArray("info") } ?: return emptyList()
        return (0 until info.length()).map(info::getJSONObject).mapNotNull { song ->
            KugouCandidate(
                id = song.optString("hash").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                accessKey = "",
                song = song.optString("songname"),
                singer = song.optString("singername"),
                durationMs = song.optLong("duration", 0) * 1000,
            )
        }.filter { KugouMatch.matches(it, title, artist, durationMs) }.take(MAX_CATALOG_SONGS)
    }

    private suspend fun download(candidate: KugouCandidate, title: String): SyncedLyrics? {
        val url = BASE.newBuilder().addPathSegment("download")
            .addQueryParameter("ver", "1").addQueryParameter("client", "pc")
            .addQueryParameter("id", candidate.id).addQueryParameter("accesskey", candidate.accessKey)
            .addQueryParameter("fmt", "krc").addQueryParameter("charset", "utf8")
            .build()
        val content = get(url)?.let { JSONObject(it).optString("content") }?.takeIf { it.isNotBlank() } ?: return null
        val krc = KrcConverter.decrypt(Base64.getDecoder().decode(content)) ?: return null
        val lrc = KrcConverter.toEnhancedLrc(krc, title) ?: return null
        return SyncedLyrics(lrc, candidate.durationMs)
    }

    private fun parseCandidates(json: String): List<KugouCandidate> {
        val list = JSONObject(json).optJSONArray("candidates") ?: return emptyList()
        return (0 until list.length()).map(list::getJSONObject).mapNotNull { c ->
            KugouCandidate(
                id = c.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                accessKey = c.optString("accesskey").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                song = c.optString("song"),
                singer = c.optString("singer"),
                durationMs = c.optLong("duration", 0),
            )
        }
    }

    private suspend fun get(url: HttpUrl): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return http.newCall(request).await().use { r -> if (r.isSuccessful) r.body.string() else null }
    }

    private companion object {
        val BASE = "https://lyrics.kugou.com/".toHttpUrl()
        /** Catalogue musical : HTTP seulement (certificat HTTPS invalide), cf. network_security_config. */
        val CATALOG = "http://mobilecdn.kugou.com/api/v3/search/song".toHttpUrl()
        const val MAX_CATALOG_SONGS = 2
        const val USER_AGENT = "KaraokeDrive/0.1 (prototype personnel)"
    }
}

/** Choix du résultat Kugou : même titre, même artiste et, si on la connaît, même durée. */
object KugouMatch {

    private val DIACRITICS = Regex("""\p{Mn}+""")
    private val NON_ALNUM = Regex("""[^a-z0-9]+""")

    fun best(candidates: List<KugouCandidate>, title: String, artist: String, durationMs: Long): KugouCandidate? =
        candidates.firstOrNull { matches(it, title, artist, durationMs) }

    fun matches(candidate: KugouCandidate, title: String, artist: String, durationMs: Long): Boolean {
        val wantedTitle = normalize(title)
        val wantedArtist = normalize(artist)
        if (wantedTitle.isEmpty() || wantedArtist.isEmpty()) return false
        return overlaps(normalize(candidate.song), wantedTitle) &&
            overlaps(normalize(candidate.singer), wantedArtist) &&
            (durationMs <= 0 || candidate.durationMs <= 0 || abs(candidate.durationMs - durationMs) <= MAX_DURATION_GAP_MS)
    }

    private fun overlaps(a: String, b: String) = a.isNotEmpty() && (a.contains(b) || b.contains(a))

    private fun normalize(value: String): String {
        val ascii = DIACRITICS.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
        return NON_ALNUM.replace(ascii.lowercase(), " ").trim()
    }

    private const val MAX_DURATION_GAP_MS = 4_000L
}
