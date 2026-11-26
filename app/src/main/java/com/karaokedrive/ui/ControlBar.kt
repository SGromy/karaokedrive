package com.karaokedrive.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karaokedrive.karaoke.KaraokeUiState

private val NeonPink = Color(0xFFFF3D8B)
private val NeonCyan = Color(0xFF3DE4FF)
private val Amber = Color(0xFFFFC247)
private val Soft = Color(0xFFB9B2E0)
private val NeonBorder = Brush.linearGradient(listOf(NeonPink.copy(alpha = 0.7f), NeonCyan.copy(alpha = 0.7f)))

/**
 * Barre de réglages pendant le karaoké : réglage du décalage et boutons ronds néon
 * (Rescanner, Stop). Réglages : roue dentée en haut à droite. Sur une ligne en paysage, sur deux en portrait.
 */
@Composable
fun ControlBar(state: KaraokeUiState, actions: KaraokeActions, modifier: Modifier = Modifier) {
    val onCalibrate = actions.onCalibrate
    BoxWithConstraints(modifier.navigationBarsPadding().padding(16.dp)) {
        val wide = maxWidth >= WIDE_LAYOUT
        // Pas de cadre : les boutons sont posés directement sur le fond de la scène
        val panel = Modifier
            .widthIn(max = if (wide) 640.dp else 440.dp)
            .fillMaxWidth()
            .padding(horizontal = if (wide) 18.dp else 4.dp, vertical = 8.dp)
        if (wide) {
            Row(panel, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                CalibrationStepper(state.calibrationMs, onCalibrate)
                Actions(actions)
            }
        } else {
            Column(panel, horizontalAlignment = Alignment.CenterHorizontally) {
                CalibrationStepper(state.calibrationMs, onCalibrate)
                Spacer(Modifier.height(12.dp))
                Actions(actions, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Actions(actions: KaraokeActions, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        RoundAction(label = "Rescanner", onClick = actions.onRescan) { NeonRing(); RefreshIcon() }
        RoundAction(label = "Stop", labelColor = NeonPink, onClick = actions.onStop) { FilledDisc(); StopIcon() }
    }
}

/** Réglage du décalage : − valeur + (pas de 0,1 s). */
@Composable
private fun CalibrationStepper(calibrationMs: Long, onCalibrate: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(minus = true) { onCalibrate(-1) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(112.dp)) {
            Text("Décalage", color = Soft.copy(alpha = 0.85f), fontSize = 11.sp, letterSpacing = 1.sp, style = LegibleOnArt)
            Text(
                "%+.1f s".format(calibrationMs / 1000f),
                color = Amber, fontSize = 19.sp, fontWeight = FontWeight.Bold, style = LegibleOnArt,
            )
        }
        StepButton(minus = false) { onCalibrate(1) }
    }
}

@Composable
private fun StepButton(minus: Boolean, onClick: () -> Unit) {
    PressableCircle(size = 38.dp, onClick = onClick, description = if (minus) "Paroles plus tôt" else "Paroles plus tard") {
        Canvas(Modifier.size(38.dp)) {
            drawCircle(brush = NeonBorder, style = Stroke(1.5.dp.toPx()))
            val half = size.minDimension * 0.22f
            val stroke = 2.5.dp.toPx()
            drawLine(Color.White, Offset(center.x - half, center.y), Offset(center.x + half, center.y), stroke, StrokeCap.Round)
            if (!minus) drawLine(Color.White, Offset(center.x, center.y - half), Offset(center.x, center.y + half), stroke, StrokeCap.Round)
        }
    }
}

/** Bouton rond de 56 dp avec sa légende, qui s'enfonce et vibre au toucher. */
@Composable
private fun RoundAction(
    label: String,
    onClick: () -> Unit,
    labelColor: Color = Soft,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(ACTION_SLOT)) {
        PressableCircle(size = ACTION_SIZE, onClick = onClick, description = label) {
            Box(Modifier.size(ACTION_SIZE), contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = LegibleOnArt)
    }
}

@Composable
private fun PressableCircle(size: Dp, onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "appui $description")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = description) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
    ) { content() }
}

// --- Icônes dessinées (cohérentes avec le style néon, sans dépendance d'icônes) -------------

@Composable
private fun NeonRing(color: Color? = null) = Canvas(Modifier.size(ACTION_SIZE)) {
    drawCircle(Color.White.copy(alpha = 0.06f))
    val stroke = Stroke(2.dp.toPx())
    if (color != null) drawCircle(color, radius = size.minDimension / 2 - stroke.width, style = stroke)
    else drawCircle(NeonBorder, radius = size.minDimension / 2 - stroke.width, style = stroke)
}

@Composable
private fun FilledDisc() = Canvas(Modifier.size(ACTION_SIZE)) {
    drawCircle(Brush.linearGradient(listOf(NeonPink, Color(0xFFB0306E))))
}

@Composable
private fun RefreshIcon() = Canvas(Modifier.size(24.dp)) {
    val stroke = 2.5.dp.toPx()
    val inset = stroke
    drawArc(
        color = Color.White, startAngle = -60f, sweepAngle = 300f, useCenter = false,
        topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    arrowHead(Offset(size.width * 0.78f, size.height * 0.1f))
}

private fun DrawScope.arrowHead(tip: Offset) {
    val stroke = 2.5.dp.toPx()
    val length = size.minDimension * 0.28f
    drawLine(Color.White, tip, Offset(tip.x - length, tip.y + length * 0.15f), stroke, StrokeCap.Round)
    drawLine(Color.White, tip, Offset(tip.x + length * 0.1f, tip.y + length), stroke, StrokeCap.Round)
}

/** Roue dentée (8 dents autour d'un anneau), dessinée pour rester dans le style néon. */
@Composable
internal fun GearIcon(modifier: Modifier, color: Color) = Canvas(modifier) {
    val r = size.minDimension / 2
    repeat(GEAR_TEETH) { i ->
        rotate(i * 360f / GEAR_TEETH, center) {
            drawRoundRect(
                color, topLeft = Offset(center.x - r * 0.15f, center.y - r * 0.98f), size = Size(r * 0.3f, r * 0.45f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.07f),
            )
        }
    }
    drawCircle(color, radius = r * 0.55f, center = center, style = Stroke(r * 0.32f))
}

@Composable
private fun StopIcon() = Canvas(Modifier.size(18.dp)) {
    drawRoundRect(Color.White, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
}

/** Ombre douce sous les textes : lisibles même sur une jaquette claire, sans panneau derrière. */
private val LegibleOnArt = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), blurRadius = 8f))

private val ACTION_SIZE = 56.dp
private const val GEAR_TEETH = 8
/** Largeur d'un bouton avec sa légende (« Rescanner » tient sur une ligne). */
private val ACTION_SLOT = 68.dp
/** Au-delà, la barre tient sur une seule ligne (paysage, tablette). */
private val WIDE_LAYOUT = 560.dp

// --- Mode entraînement ------------------------------------------------------------------------

/**
 * Barre du mode entraînement (paroles sans musique) : lecture/pause au centre, ±10 s,
 * recommencer, vitesse et quitter (réglages : roue dentée en haut à droite).
 */
@Composable
fun PracticeBar(state: KaraokeUiState, actions: KaraokeActions, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.navigationBarsPadding().padding(16.dp)) {
        val wide = maxWidth >= WIDE_LAYOUT
        val transport = @Composable {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                RoundAction(label = "−10 s", onClick = { actions.onPracticeSeek(-SEEK_MS) }) { NeonRing(); StepLabel("−10") }
                RoundAction(
                    label = if (state.practicePlaying) "Pause" else "Lecture",
                    labelColor = NeonPink,
                    onClick = actions.onPracticePlayPause,
                ) { FilledDisc(); if (state.practicePlaying) PauseIcon() else PlayIcon() }
                RoundAction(label = "+10 s", onClick = { actions.onPracticeSeek(SEEK_MS) }) { NeonRing(); StepLabel("+10") }
            }
        }
        val options = @Composable { rowModifier: Modifier ->
            Row(rowModifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                RoundAction(label = "Début", onClick = actions.onPracticeRestart) { NeonRing(); RefreshIcon() }
                RoundAction(label = "Vitesse", onClick = actions.onPracticeSpeed) {
                    NeonRing(); StepLabel(com.karaokedrive.render.formatSpeed(state.practiceSpeed))
                }
                RoundAction(label = "Quitter", onClick = actions.onStop) { NeonRing(); StopIcon() }
            }
        }
        val panel = Modifier.widthIn(max = if (wide) 820.dp else 440.dp).fillMaxWidth().padding(horizontal = if (wide) 12.dp else 4.dp, vertical = 8.dp)
        if (wide) {
            Row(panel, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                transport()
                options(Modifier)
            }
        } else {
            Column(panel, horizontalAlignment = Alignment.CenterHorizontally) {
                transport()
                Spacer(Modifier.height(10.dp))
                options(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StepLabel(text: String) =
    Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

@Composable
private fun PlayIcon() = Canvas(Modifier.size(22.dp)) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(size.width * 0.22f, size.height * 0.1f)
        lineTo(size.width * 0.92f, size.height * 0.5f)
        lineTo(size.width * 0.22f, size.height * 0.9f)
        close()
    }
    drawPath(path, Color.White)
}

@Composable
private fun PauseIcon() = Canvas(Modifier.size(20.dp)) {
    val bar = size.width * 0.3f
    val radius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
    drawRoundRect(Color.White, topLeft = Offset(size.width * 0.1f, 0f), size = Size(bar, size.height), cornerRadius = radius)
    drawRoundRect(Color.White, topLeft = Offset(size.width * 0.6f, 0f), size = Size(bar, size.height), cornerRadius = radius)
}

private const val SEEK_MS = 10_000L
