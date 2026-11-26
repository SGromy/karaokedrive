package com.karaokedrive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karaokedrive.karaoke.KaraokeController
import com.karaokedrive.service.ListeningService

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) ListeningService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KaraokeController.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val state by KaraokeController.state.collectAsStateWithLifecycle()
            val favorites by KaraokeController.favorites.collectAsStateWithLifecycle()
            // Échecs ponctuels (favori non enregistré…) : signalés au lieu d'être ignorés
            LaunchedEffect(Unit) {
                KaraokeController.notices.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
            }
            KaraokeApp(state = state, favorites = favorites, coverOf = KaraokeController::favoriteCover, actions = actions)
        }
    }

    private val actions by lazy {
        KaraokeActions(
            onListen = ::startListening,
            onStop = {
                ListeningService.stop(this)
                KaraokeController.stop()
            },
            onRescan = KaraokeController::rescan,
            onCalibrate = KaraokeController::nudgeCalibration,
            onToggleBall = KaraokeController::toggleBall,
            onToggleEffects = KaraokeController::toggleEffects,
            onToggleTranslation = KaraokeController::toggleTranslation,
            onToggleFavorite = KaraokeController::toggleFavorite,
            onOpenFavorite = { id ->
                ListeningService.stop(this) // pas de micro pendant l'entraînement
                KaraokeController.startPractice(id)
            },
            onDeleteFavorite = KaraokeController::deleteFavorite,
            onPracticePlayPause = KaraokeController::practiceTogglePlay,
            onPracticeSeek = KaraokeController::practiceSeek,
            onPracticeRestart = KaraokeController::practiceRestart,
            onPracticeSpeed = KaraokeController::practiceNextSpeed,
        )
    }

    private fun startListening() {
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            ListeningService.start(this)
        } else {
            permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }
    }
}
