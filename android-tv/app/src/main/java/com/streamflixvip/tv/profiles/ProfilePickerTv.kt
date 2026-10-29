package com.streamflixvip.tv.profiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.streamflixvip.tv.data.TvSettingsStore
import com.streamflixvip.tv.ui.components.TvButton
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay

@Composable
fun TvProfileAvatar(avatar: String, name: String, size: Dp, focused: Boolean = false, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val res = remember(avatar) { Avatars.resId(ctx, avatar) }
    val shape = RoundedCornerShape(size * 0.2f)
    Box(
        modifier.size(size).clip(shape)
            .border(if (focused) 4.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.15f), shape)
            .background(Brush.linearGradient(listOf(p.accent, p.accent2))),
        contentAlignment = Alignment.Center,
    ) {
        if (res != 0) Image(painterResource(res), name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.4f).sp)
    }
}

/** Aplica o perfil escolhido: perfil infantil liga o Modo Infantil da TV (e o contrário desliga). */
fun applyProfile(profile: Profile) {
    ProfileStore.select(profile)
    runCatching { TvSettingsStore.update { it.copy(kidsMode = profile.kids) } }
}

/** "Quem está assistindo?" na TV — tudo no controle remoto (sem listas lazy). */
@Composable
fun ProfilePickerTvScreen(onPicked: () -> Unit) {
    val p = LocalPalette.current
    val profiles by ProfileStore.profiles.collectAsState()
    var manage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(profiles.size, editing, creating, pinFor) {
        if (editing == null && !creating && pinFor == null) { delay(80); runCatching { first.requestFocus() } }
    }

    Box(Modifier.fillMaxSize().background(p.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (manage) "Gerenciar perfis" else "Quem está assistindo?", color = p.text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                profiles.forEachIndexed { i, prof ->
                    Tile(prof.name, prof.avatar, sub = if (prof.kids) "INFANTIL" else null, lock = prof.hasPin, edit = manage,
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) {
                        when {
                            manage -> editing = prof
                            prof.hasPin -> pinFor = prof
                            else -> { applyProfile(prof); onPicked() }
                        }
                    }
                }
                if (profiles.size < ProfileStore.MAX_PROFILES) {
                    Tile("Adicionar", null, sub = null, lock = false, edit = false) { creating = true }
                }
            }
            Spacer(Modifier.height(34.dp))
            TvButton(if (manage) "Concluído" else "Gerenciar perfis", onClick = { manage = !manage }, icon = Icons.Filled.Edit)
        }
    }

    if (creating || editing != null) {
        ProfileEditorTv(
            initial = editing,
            canDelete = editing != null && profiles.size > 1,
            onClose = { creating = false; editing = null },
        )
    }
    pinFor?.let { prof ->
        PinPadTv("PIN de ${prof.name}", onCancel = { pinFor = null }) { pin ->
            if (ProfileStore.checkPin(prof, pin)) { pinFor = null; applyProfile(prof); onPicked(); true } else false
        }
    }
}

@Composable
private fun Tile(name: String, avatar: String?, sub: String?, lock: Boolean, edit: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val s by animateFloatAsState(if (focused) 1.1f else 1f, label = "tile")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.scale(s).clickable(interactionSource = src, indication = null, onClick = onClick),
    ) {
        Box {
            if (avatar != null) TvProfileAvatar(avatar, name, 132.dp, focused)
            else Box(
                Modifier.size(132.dp).clip(RoundedCornerShape(26.dp))
                    .border(if (focused) 4.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.3f), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
            if (edit) Box(Modifier.matchParentSize().clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            if (lock) Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(name, color = if (focused) p.text else p.textMuted, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (sub != null) Text(sub, color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileEditorTv(initial: Profile?, canDelete: Boolean, onClose: () -> Unit) {
    val p = LocalPalette.current
    val id = remember { initial?.id ?: java.util.UUID.randomUUID().toString() }
    var name by remember { mutableStateOf(initial?.name ?: "Perfil ${ProfileStore.profiles.value.size + 1}") }
    var avatar by remember { mutableStateOf(initial?.avatar ?: Avatars.ADULT.random()) }
    var kids by remember { mutableStateOf(initial?.kids ?: false) }
    var pinHash by remember { mutableStateOf(initial?.pinHash) }
    var settingPin by remember { mutableStateOf(false) }
    val saveFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { saveFocus.requestFocus() } }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.width(760.dp).heightIn(max = 520.dp).clip(RoundedCornerShape(20.dp)).background(p.bgElevated)
                .verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvProfileAvatar(avatar, name, 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (initial == null) "Novo perfil" else "Editar perfil", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    BasicTextField(
                        value = name, onValueChange = { name = it.take(Profile.MAX_NAME) }, singleLine = true,
                        textStyle = TextStyle(color = p.text, fontSize = 18.sp), cursorBrush = SolidColor(p.accent),
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(p.surfaceHi).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Salvar", primary = true, modifier = Modifier.focusRequester(saveFocus), onClick = {
                    ProfileStore.upsert(Profile(id, name.ifBlank { "Perfil" }, avatar, kids, pinHash)); onClose()
                })
                TvButton(if (kids) "Infantil: Sim" else "Infantil: Não", onClick = { kids = !kids })
                TvButton(if (pinHash != null) "Remover PIN" else "Definir PIN", onClick = { if (pinHash != null) pinHash = null else settingPin = true })
                if (canDelete && initial != null) TvButton("Excluir", onClick = { ProfileStore.delete(initial.id); onClose() })
                TvButton("Cancelar", onClick = onClose)
            }
            Spacer(Modifier.height(18.dp))
            Text("Avatar", color = p.textMuted, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            (if (kids) Avatars.KIDS + Avatars.ADULT else Avatars.ALL).chunked(9).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                    row.forEach { key ->
                        val src = remember { MutableInteractionSource() }
                        val f by src.collectIsFocusedAsState()
                        TvProfileAvatar(key, key, 66.dp, focused = f || key == avatar,
                            modifier = Modifier.clickable(interactionSource = src, indication = null) { avatar = key })
                    }
                }
            }
        }
    }
    if (settingPin) {
        PinPadTv("Crie um PIN de 4 dígitos", onCancel = { settingPin = false }) { pin ->
            pinHash = ProfileStore.hashPin(id, pin); settingPin = false; true
        }
    }
}

/** Teclado numérico para o controle remoto. [onSubmit] devolve false para "PIN incorreto". */
@Composable
fun PinPadTv(title: String, onCancel: () -> Unit, onSubmit: (String) -> Boolean) {
    val p = LocalPalette.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    fun press(d: String) {
        if (pin.length >= 4) return
        pin += d; error = false
        if (pin.length == 4) { if (!onSubmit(pin)) { error = true; pin = "" } }
    }
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(p.bgElevated).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("● ".repeat(pin.length) + "○ ".repeat(4 - pin.length), color = if (error) p.live else p.text, fontSize = 26.sp)
            if (error) Text("PIN incorreto", color = p.live, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✕")).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                    row.forEachIndexed { c, k ->
                        TvButton(k, modifier = Modifier.width(72.dp).then(if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier), onClick = {
                            when (k) { "⌫" -> pin = pin.dropLast(1); "✕" -> onCancel(); else -> press(k) }
                        })
                    }
                }
            }
        }
    }
}
