package com.karaokedrive.car

import android.graphics.BitmapFactory
import androidx.car.app.AppManager
import androidx.core.content.res.ResourcesCompat
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.karaokedrive.R
import com.karaokedrive.karaoke.KaraokeController
import com.karaokedrive.karaoke.Status
import com.karaokedrive.service.ListeningService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Écran central : un NavigationTemplate « vide » dont on s'approprie la surface
 * pour dessiner la scène karaoké, avec quelques boutons dans la barre d'actions.
 */
class KaraokeCarScreen(carContext: CarContext) : Screen(carContext) {

    private val renderer = CarSurfaceRenderer(
        BitmapFactory.decodeResource(carContext.resources, R.drawable.logo_badge),
        ResourcesCompat.getFont(carContext, R.font.nunito_semibold_italic),
    )

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = renderer.release()
        })
        // Les boutons changent selon l'état (écoute / arrêt) : on redemande le template.
        lifecycleScope.launch {
            KaraokeController.state
                .map { listOf(it.status == Status.IDLE, it.status == Status.SINGING, it.showBall, it.isPractice, it.practicePlaying) }
                .distinctUntilChanged()
                .collect { invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        val isIdle = KaraokeController.state.value.status == Status.IDLE
        val strip = ActionStrip.Builder().apply {
            if (isIdle) {
                addAction(action("🎤 Écouter") { startListening() })
            } else if (KaraokeController.state.value.isPractice) {
                // Entraînement : lecture/pause et arrêt (pas de reconnaissance)
                val playing = KaraokeController.state.value.practicePlaying
                addAction(action(if (playing) "⏸ Pause" else "▶ Lecture") { KaraokeController.practiceTogglePlay() })
                addAction(action("Stop") { KaraokeController.stop() })
            } else {
                addAction(action("⟳ Rescanner") { KaraokeController.rescan() })
                val state = KaraokeController.state.value
                addAction(action(if (state.showBall) "● Boule" else "○ Boule") { KaraokeController.toggleBall() })
                addAction(action("Stop") { ListeningService.stop(carContext); KaraokeController.stop() })
            }
        }.build()
        return NavigationTemplate.Builder().setActionStrip(strip).build()
    }

    private fun startListening() {
        // Android peut refuser d'ouvrir le micro depuis l'arrière-plan :
        // dans ce cas il faut lancer l'écoute une fois depuis le téléphone.
        runCatching { ListeningService.start(carContext) }.onFailure {
            CarToast.makeText(carContext, "Lancez l'écoute depuis le téléphone", CarToast.LENGTH_LONG).show()
        }
    }

    private fun action(title: String, onClick: () -> Unit): Action =
        Action.Builder().setTitle(title).setOnClickListener(onClick).build()
}
