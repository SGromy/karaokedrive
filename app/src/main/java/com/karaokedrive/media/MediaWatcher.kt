package com.karaokedrive.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.karaokedrive.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Suit les applications média du téléphone et expose celle qui joue. */
object MediaWatcher {

    private const val TAG = "KaraokeDrive"

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var ownPackage: String = ""
    private var packageManager: PackageManager? = null

    /** Noms connus, car Android masque souvent les autres applis (visibilité des paquets). */
    private val KNOWN_APPS = mapOf(
        "com.appmind.radios.fr" to "Radios France",
        "com.spotify.music" to "Spotify",
        "deezer.android.app" to "Deezer",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.amazon.mp3" to "Amazon Music",
        "com.apple.android.music" to "Apple Music",
    )
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        track(controllers.orEmpty())
    }

    fun start(context: Context) {
        val sessions = context.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(context, MediaListenerService::class.java)
        manager = sessions
        ownPackage = context.packageName
        packageManager = context.packageManager
        runCatching {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            track(sessions.getActiveSessions(component))
        }.onFailure { Log.w(TAG, "sessions média inaccessibles : ${it.message}") }
    }

    fun stop() {
        manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        manager = null
        untrackAll()
        _nowPlaying.value = null
    }

    private fun track(controllers: List<MediaController>) {
        untrackAll()
        controllers.filter { it.packageName != ownPackage }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
                override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
            }
            controller.registerCallback(callback, handler)
            callbacks[controller] = callback
        }
        refresh()
    }

    private fun untrackAll() {
        callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        callbacks.clear()
    }

    private fun refresh() {
        val playing = callbacks.keys.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val next = playing?.let(::toNowPlaying)
        if (next != _nowPlaying.value) {
            if (BuildConfig.DEBUG) Log.d(TAG, "média : $next")
            _nowPlaying.value = next
        }
    }

    private fun labelOf(packageName: String): String =
        KNOWN_APPS[packageName]
            ?: packageManager?.let { pm ->
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrNull()
            }
            ?: packageName.substringAfterLast('.')

    private fun toNowPlaying(controller: MediaController): NowPlaying? {
        val metadata = controller.metadata ?: return null
        val state = controller.playbackState
        if (BuildConfig.DEBUG) {
            val fields = metadata.keySet().filter { it.contains("TITLE") || it.contains("ARTIST") || it.contains("ALBUM") || it.contains("DURATION") || it.contains("DISPLAY") }
                .joinToString { key -> "$key=${metadata.getText(key) ?: metadata.getLong(key)}" }
            Log.d(TAG, "brut ${controller.packageName} : $fields | pos=${state?.position}")
        }
        return NowPlayingParser.parse(
            packageName = controller.packageName,
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            displaySubtitle = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = state?.position ?: 0,
            positionUpdatedAtMs = state?.lastPositionUpdateTime ?: 0,
            speed = state?.playbackSpeed ?: 1f,
            appLabel = labelOf(controller.packageName),
        )
    }
}
