package com.karaokedrive.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karaokedrive.favorites.Favorite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Night = Color(0xFF07051A)
private val Plum = Color(0xFF1B1045)
private val Pink = Color(0xFFFF3D8B)
private val Lilac = Color(0xFFB9B2E0)

/** Liste des favoris : toucher un morceau lance l'entraînement (paroles sans musique). */
@Composable
fun FavoritesScreen(
    favorites: List<Favorite>,
    coverOf: (String) -> Bitmap?,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
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
            Text("Mes favoris", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Aucun favori pour l'instant.\nPendant un karaoké, touchez ♥ pour garder le morceau et réviser ses paroles, même sans musique.",
                    color = Lilac, fontSize = 16.sp, textAlign = TextAlign.Center,
                )
            }
        } else {
            Text(
                "Touchez un morceau pour réviser ses paroles sans musique.",
                color = Lilac.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp),
            )
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favorites, key = { it.id }) { favorite ->
                    FavoriteRow(favorite, coverOf, onOpen = { onOpen(favorite.id) }, onDelete = { onDelete(favorite.id) })
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(favorite: Favorite, coverOf: (String) -> Bitmap?, onOpen: () -> Unit, onDelete: () -> Unit) {
    // Lecture et décodage sur disque hors du fil de l'interface : la liste défile sans à-coups
    val cover by produceState<ImageBitmap?>(null, favorite.id) {
        value = withContext(Dispatchers.IO) { coverOf(favorite.id)?.asImageBitmap() }
    }
    var confirmDelete by remember(favorite.id) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClickLabel = "Réviser les paroles", onClick = onOpen)
            .padding(10.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Plum), contentAlignment = Alignment.Center) {
            val image = cover
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
            } else {
                Text("♪", color = Lilac, fontSize = 24.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(favorite.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${favorite.artist} · ${formatDuration(favorite.durationMs)}",
                color = Lilac, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // Suppression en deux touches, pour éviter les erreurs
        Text(
            if (confirmDelete) "Supprimer ?" else "✕",
            color = if (confirmDelete) Pink else Lilac.copy(alpha = 0.7f),
            fontSize = if (confirmDelete) 14.sp else 20.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClickLabel = "Supprimer ce favori") { if (confirmDelete) onDelete() else confirmDelete = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private fun formatDuration(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)
