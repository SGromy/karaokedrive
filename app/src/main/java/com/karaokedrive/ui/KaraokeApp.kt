package com.karaokedrive.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.karaokedrive.R
import com.karaokedrive.favorites.Favorite
import com.karaokedrive.karaoke.KaraokeUiState
import com.karaokedrive.karaoke.Status
import com.karaokedrive.render.KaraokeRenderer
import com.karaokedrive.render.RenderCadence
import com.karaokedrive.render.drawState

private val Pink = Color(0xFFFF3D8B)
private val Cyan = Color(0xFF3DE4FF)
private val Ink = Color(0xFF07051A)
private val Lilac = Color(0xFF8C84B8)

/** États où l'écran montre le grand bouton rond plutôt que les paroles. */
private val WAITING = setOf(Status.IDLE, Status.LISTENING, Status.NO_MATCH, Status.ERROR)

/**
 * Écran unique : la scène du karaoké, avec le logo en grand bouton rond tant qu'aucune
 * chanson n'est affichée, puis les paroles et la barre de réglages.
 */
@Composable
fun KaraokeApp(
    state: KaraokeUiState,
    favorites: List<Favorite>,
    coverOf: (String) -> Bitmap?,
    actions: KaraokeActions,
) {
    var page by rememberSaveable { mutableStateOf(Page.STAGE) }
    when (page) {
        Page.FAVORITES -> {
            FavoritesScreen(
                favorites = favorites,
                coverOf = coverOf,
                onOpen = { id -> page = Page.STAGE; actions.onOpenFavorite(id) },
                onDelete = actions.onDeleteFavorite,
                onBack = { page = Page.STAGE },
            )
            return
        }
        Page.SETTINGS -> {
            SettingsScreen(state, actions, onBack = { page = Page.STAGE })
            return
        }
        Page.STAGE -> Unit
    }
    val openSettings = { page = Page.SETTINGS }
    val context = LocalContext.current
    val renderer = remember {
        KaraokeRenderer(
            logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo_badge),
            translationTypeface = ResourcesCompat.getFont(context, R.font.nunito_semibold_italic),
        )
    }
    // Paroles qui défilent : redessin à chaque frame (la position vient de l'horloge, pas de
    // l'état). Sinon l'image est fixe : redessinée seulement quand l'état change.
    var frameTick by remember { mutableLongStateOf(0L) }
    val animated = RenderCadence.isAnimated(state)
    LaunchedEffect(animated) { if (animated) while (true) withFrameMillis { frameTick = it } }

    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    // Place du cœur et de la roue dentée en haut à droite : le titre s'arrête avant
    renderer.headerRightInset = with(density) { (HEART_ZONE * 2).toPx() }
    val isWaiting = state.status in WAITING
    // Haut de la barre de réglages : la scène se dessine au-dessus pour ne jamais être recouverte
    var barTop by remember { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Canvas(Modifier.fillMaxSize()) {
            frameTick.let { } // lecture = abonnement au tick
            drawIntoCanvas { canvas ->
                val hasBar = !isWaiting && barTop > topInset
                // Paysage : la barre occupe le bas de l'écran, les paroles se centrent au-dessus.
                // Portrait : la place ne manque pas, on garde tout l'écran (jaquette en grand) et
                // seule la ligne suivante est masquée si elle atteignait la barre.
                val landscape = size.width > size.height
                val bottom = if (hasBar && landscape) barTop else size.height
                renderer.backdropArea = RectF(0f, topInset, size.width, size.height)
                renderer.contentBottom = if (hasBar) barTop else null
                renderer.drawState(
                    canvas.nativeCanvas, RectF(0f, topInset, size.width, bottom), state,
                    drawWaitingScreens = false, effects = state.showEffects,
                )
            }
        }
        val trackBar = Modifier.align(Alignment.BottomCenter).onGloballyPositioned { barTop = it.positionInRoot().y }
        when {
            isWaiting -> WaitingPanel(
                state, favoritesCount = favorites.size,
                onListen = actions.onListen, onStop = actions.onStop,
                onFavorites = { page = Page.FAVORITES },
                modifier = Modifier.align(Alignment.Center),
            )
            state.isPractice -> PracticeBar(state, actions, trackBar)
            else -> ControlBar(state, actions, trackBar)
        }
        // En haut à droite : réglages tout au bord, et le cœur des favoris juste à sa gauche
        val topRight = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 4.dp, end = 8.dp)
        SettingsGear(openSettings, topRight)
        if (state.status == Status.SINGING && state.lyrics != null) {
            FavoriteHeart(
                isFavorite = state.isFavorite, onClick = actions.onToggleFavorite,
                modifier = topRight.padding(end = HEART_ZONE),
            )
        }
    }
}

/** Pages plein écran de l'application. */
private enum class Page { STAGE, FAVORITES, SETTINGS }

@Composable
private fun SettingsGear(onClick: () -> Unit, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(HEART_ZONE)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClickLabel = "Réglages",
                onClick = onClick,
            ),
    ) { GearIcon(Modifier.size(26.dp), Color.White.copy(alpha = 0.85f)) }
}

/** Cœur en haut à droite : ajoute le morceau aux favoris (plein) ou l'en retire. */
@Composable
private fun FavoriteHeart(isFavorite: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    Text(
        text = if (isFavorite) "♥" else "♡",
        color = if (isFavorite) Pink else Color.White.copy(alpha = 0.85f),
        fontSize = 30.sp,
        modifier = modifier
            .size(HEART_ZONE)
            .wrapContentSize(Alignment.Center)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClickLabel = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
    )
}

/** Grand bouton d'écoute et texte d'état (repos, recherche, non reconnu, erreur). */
@Composable
private fun WaitingPanel(
    state: KaraokeUiState,
    favoritesCount: Int,
    onListen: () -> Unit,
    onStop: () -> Unit,
    onFavorites: () -> Unit,
    modifier: Modifier,
) {
    val isSearching = state.status == Status.LISTENING || state.status == Status.NO_MATCH
    val (title, subtitle) = when (state.status) {
        Status.LISTENING -> "J'écoute la radio…" to "Touchez pour arrêter"
        Status.NO_MATCH -> "Morceau non reconnu" to "Nouvel essai dans un instant"
        Status.ERROR -> "Oups" to (state.message ?: "Touchez pour réessayer")
        else -> "Touchez pour chanter" to "La radio passe, toute la voiture chante."
    }
    BoxWithConstraints(modifier.padding(24.dp)) {
        // Bouton aussi grand que possible, sans déborder (paysage compris)
        val side = min(min(maxWidth * 0.62f, maxHeight * 0.5f), 300.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ListenButton(
                isSearching = isSearching,
                side = side,
                onClick = if (isSearching) onStop else onListen,
            )
            Spacer(Modifier.height(20.dp))
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Lilac, fontSize = 15.sp, textAlign = TextAlign.Center)
            if (state.status == Status.IDLE) {
                Spacer(Modifier.height(24.dp))
                FavoritesButton(favoritesCount, onFavorites)
                Spacer(Modifier.height(20.dp))
                Text(
                    "Branchez le téléphone : les paroles s'affichent aussi sur l'écran de la voiture.",
                    color = Lilac.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
        }
    }
}

/** Accès à la page des favoris (révision des paroles sans musique). */
@Composable
private fun FavoritesButton(count: Int, onClick: () -> Unit) {
    Text(
        text = if (count > 0) "♥  Mes favoris ($count)" else "♥  Mes favoris",
        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, Brush.linearGradient(listOf(Pink, Cyan)), RoundedCornerShape(24.dp))
            .clickable(onClickLabel = "Ouvrir mes favoris", onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    )
}

/**
 * Logo rond servant de bouton. Au repos, un halo respire doucement ; pendant la
 * recherche, des ondes néon s'en échappent en continu et le logo pulse.
 */
@Composable
private fun ListenButton(isSearching: Boolean, side: Dp, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "écoute")
    val wave by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WAVE_MS, easing = LinearEasing)), label = "ondes",
    )
    val breath by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MS), RepeatMode.Reverse), label = "respiration",
    )
    val haloSide = side * 1.45f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(haloSide)) {
        Canvas(Modifier.size(haloSide)) {
            val radius = side.toPx() / 2
            if (isSearching) {
                repeat(WAVES) { i ->
                    val progress = (wave + i.toFloat() / WAVES) % 1f
                    drawCircle(
                        color = lerp(Pink, Cyan, i.toFloat() / (WAVES - 1)).copy(alpha = (1f - progress) * 0.7f),
                        radius = radius * (0.95f + progress * 0.45f),
                        style = Stroke(width = (1f - progress) * 10.dp.toPx() + 1.dp.toPx()),
                    )
                }
            } else {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Pink.copy(alpha = 0.25f + breath * 0.2f), Cyan.copy(alpha = 0.08f), Color.Transparent),
                        radius = radius * 1.4f,
                    ),
                    radius = radius * 1.4f,
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.logo_badge),
            contentDescription = if (isSearching) "Arrêter l'écoute" else "Écouter la radio",
            modifier = Modifier
                .size(side)
                .scale(if (isSearching) 0.97f + breath * 0.06f else 1f)
                // Pas de découpe ronde : les lettres du logo débordent de l'anneau
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = side / 2),
                    onClick = onClick,
                ),
        )
    }
}

private const val WAVES = 3
private val HEART_ZONE = 48.dp
private const val WAVE_MS = 1_800
private const val BREATH_MS = 1_300
