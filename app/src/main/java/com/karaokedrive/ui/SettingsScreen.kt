package com.karaokedrive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karaokedrive.BuildConfig
import com.karaokedrive.karaoke.KaraokeUiState

private val Night = Color(0xFF07051A)
private val Plum = Color(0xFF1B1045)
private val Pink = Color(0xFFFF3D8B)
private val Lilac = Color(0xFFB9B2E0)
private val Amber = Color(0xFFFFC247)

/** Crédit affiché : ce qu'apporte le composant, puis qui le fournit et sous quelle licence. */
private data class Credit(val role: String, val source: String)

private const val AUTHOR = "Sébastien Gromy"

/** Traduction par IA active seulement si une clé est fournie dans local.properties (llm.apiKey). */
private val AI_TRANSLATION = BuildConfig.LLM_API_KEY.isNotBlank()

private val CREDITS = listOf(
    Credit("Auteur", "$AUTHOR : idée, conception et développement de KaraokeDrive"),
    Credit("Paroles synchronisées", "LRCLIB (lrclib.net), base communautaire et gratuite"),
    Credit("Timing mot par mot", "Kugou (API non officielle), format KRC documenté par Lyricify-Lyrics-Helper (Apache-2.0)"),
    Credit("Reconnaissance des morceaux", "Service Shazam (API non officielle), empreinte audio d'après SongRec (GPL-3.0) et shazamio (MIT) ; ACRCloud en secours"),
    Credit("Pochettes", "Shazam, Deezer"),
    Credit(
        "Traduction française",
        if (AI_TRANSLATION) "IA (${BuildConfig.LLM_MODEL}), et Google ML Kit sur le téléphone en secours" else "Google ML Kit, traduction sur le téléphone",
    ),
    Credit("Police de la traduction", "Nunito, The Nunito Project Authors (SIL Open Font License 1.1)"),
    Credit("Logo", "$AUTHOR, création originale pour KaraokeDrive"),
)

/** Réglages commutables, crédits et version de l'application. */
@Composable
fun SettingsScreen(state: KaraokeUiState, actions: KaraokeActions, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Plum, Night)))
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", color = Color.White, fontSize = 34.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Retour", onClick = onBack)
                    .padding(horizontal = 14.dp),
            )
            Text("Réglages", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Affichage")
            ToggleRow(
                title = "Boule qui rebondit",
                detail = "Elle saute de mot en mot au rythme des paroles.",
                checked = state.showBall,
                onToggle = actions.onToggleBall,
            )
            ToggleRow(
                title = "Effets animés",
                detail = "Onde néon derrière la pochette et, en paysage, visualiseur derrière les paroles.",
                checked = state.showEffects,
                onToggle = actions.onToggleEffects,
            )
            ToggleRow(
                title = "Traduction automatique",
                detail = if (AI_TRANSLATION) {
                    "Les paroles en langue étrangère sont traduites en français sous chaque ligne, par IA ; sans réseau, sur le téléphone (anglais seulement)."
                } else {
                    "Les paroles en anglais sont traduites en français sous chaque ligne (sur le téléphone, sans connexion)."
                },
                checked = state.showTranslation,
                onToggle = actions.onToggleTranslation,
            )
            Text(
                "Le décalage des paroles se règle pendant le karaoké, avec − et +.",
                color = Lilac.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(12.dp))
            SectionTitle("Crédits")
            Column(Card, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CREDITS.forEach { credit ->
                    Column {
                        Text(credit.role, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(credit.source, color = Lilac, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Version")
            Column(Card) {
                Text(
                    "KaraokeDrive ${BuildConfig.VERSION_NAME}",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
                Text("par $AUTHOR", color = Lilac, fontSize = 13.sp)
                Text(
                    "Build ${BuildConfig.VERSION_CODE}" + if (BuildConfig.DEBUG) " · version de test" else "",
                    color = Lilac, fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Prototype personnel : les services de paroles et de reconnaissance utilisés " +
                        "ne sont pas prévus pour une diffusion publique.",
                    color = Lilac.copy(alpha = 0.7f), fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val Card = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(18.dp))
    .background(Color.White.copy(alpha = 0.06f))
    .padding(16.dp)

@Composable
private fun SectionTitle(text: String) = Text(
    text.uppercase(), color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
)

/** Ligne entière cliquable avec son interrupteur (grande cible, utilisable d'une main). */
@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(role = Role.Switch, onClickLabel = title, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Lilac, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null, // la ligne entière gère le toucher
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Pink,
                uncheckedThumbColor = Lilac,
                uncheckedTrackColor = Night,
                uncheckedBorderColor = Lilac.copy(alpha = 0.5f),
            ),
            modifier = Modifier.widthIn(min = 52.dp),
        )
    }
}
