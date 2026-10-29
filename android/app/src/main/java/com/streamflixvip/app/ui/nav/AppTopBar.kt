package com.streamflixvip.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppTopBar(
    onSearchClick: () -> Unit,
    onFavoritesClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
) {
    val profile by com.streamflixvip.app.profiles.ProfileStore.active.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STREAM", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, color = Color.White)
            Text("FLIX", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, color = Color(0xFFBDBDBD))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onFavoritesClick != null) {
                IconButton(onClick = onFavoritesClick, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF1A1A1A))) {
                    Icon(Icons.Filled.Favorite, "Minha Lista", modifier = Modifier.size(22.dp), tint = Color.White)
                }
                Spacer(Modifier.width(6.dp))
            }
            IconButton(onClick = onSearchClick, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF1A1A1A))) {
                Icon(Icons.Filled.Search, "Buscar", modifier = Modifier.size(22.dp), tint = Color.White)
            }
            val p = profile
            if (onProfileClick != null && p != null) {
                Spacer(Modifier.width(8.dp))
                // avatar do perfil atual: toque para trocar de perfil
                com.streamflixvip.app.profiles.ProfileAvatar(
                    p.avatar, p.name, 40.dp,
                    Modifier.clip(CircleShape).clickable(onClick = onProfileClick),
                )
            }
        }
    }
}
