package com.streamflixvip.app.profiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.streamflixvip.app.ui.theme.StreamFlixColors

private val Bg = Color(0xFF070B12)

@Composable
fun ProfileAvatar(avatar: String, name: String, size: Dp, modifier: Modifier = Modifier, selected: Boolean = false) {
    val ctx = LocalContext.current
    val res = remember(avatar) { Avatars.resId(ctx, avatar) }
    val border = if (selected) BorderStroke(3.dp, StreamFlixColors.Accent) else BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).border(border, RoundedCornerShape(size * 0.22f))
            .background(Brush.linearGradient(listOf(StreamFlixColors.Accent, StreamFlixColors.Accent2))),
        contentAlignment = Alignment.Center,
    ) {
        if (res != 0) {
            Image(painterResource(res), contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.4f).sp)
        }
    }
}

/**
 * "Quem está assistindo?" — escolher, criar, editar e apagar perfis.
 * [onPicked] é chamado depois de conferir o PIN (se houver).
 */
@Composable
fun ProfilePickerScreen(onPicked: (Profile) -> Unit, onChanged: () -> Unit = {}, startInManage: Boolean = false) {
    val profiles by ProfileStore.profiles.collectAsState()
    var manage by remember { mutableStateOf(startInManage) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var askPinFor by remember { mutableStateOf<Profile?>(null) }

    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(
                if (manage) "Gerenciar perfis" else "Quem está assistindo?",
                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(118.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.widthIn(max = 560.dp).heightIn(max = 460.dp),
            ) {
                items(profiles.distinctBy { it.id }, key = { "p_" + it.id }) { p ->
                    ProfileTile(p, manage) {
                        when {
                            manage -> editing = p
                            p.hasPin -> askPinFor = p
                            else -> { ProfileStore.select(p); onPicked(p) }
                        }
                    }
                }
                if (profiles.size < ProfileStore.MAX_PROFILES) {
                    item(key = "add") { AddTile { creating = true } }
                }
            }
            Spacer(Modifier.height(26.dp))
            OutlinedButton(
                onClick = { manage = !manage },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
            ) {
                Icon(if (manage) Icons.Default.Check else Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (manage) "Concluído" else "Gerenciar perfis", color = Color.White)
            }
        }
    }

    if (creating || editing != null) {
        ProfileEditorDialog(
            initial = editing,
            canDelete = editing != null && profiles.size > 1,
            onDismiss = { creating = false; editing = null },
            onSave = { ProfileStore.upsert(it); creating = false; editing = null; onChanged() },
            onDelete = { id -> ProfileStore.delete(id); editing = null; onChanged() },
        )
    }
    askPinFor?.let { p ->
        PinDialog(
            title = "PIN de ${p.name}",
            onDismiss = { askPinFor = null },
            onSubmit = { pin ->
                if (ProfileStore.checkPin(p, pin)) { askPinFor = null; ProfileStore.select(p); onPicked(p); true } else false
            },
        )
    }
}

@Composable
private fun ProfileTile(p: Profile, manage: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val s by animateFloatAsState(if (focused) 1.08f else 1f, label = "tile")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(s).clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick).padding(6.dp),
    ) {
        Box {
            ProfileAvatar(p.avatar, p.name, 104.dp, selected = focused)
            if (manage) {
                Box(Modifier.matchParentSize().clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Edit, "Editar", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            if (p.hasPin) Icon(Icons.Default.Lock, "Com PIN", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(p.name, color = if (focused) Color.White else Color(0xFFB8C0CC), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (p.kids) Text("INFANTIL", color = StreamFlixColors.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(6.dp),
    ) {
        Box(
            Modifier.size(104.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Add, "Adicionar perfil", tint = Color.White, modifier = Modifier.size(44.dp)) }
        Spacer(Modifier.height(8.dp))
        Text("Adicionar", color = Color(0xFFB8C0CC), fontSize = 14.sp)
    }
}

@Composable
fun ProfileEditorDialog(
    initial: Profile?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var avatar by remember { mutableStateOf(initial?.avatar ?: Avatars.ADULT.random()) }
    var kids by remember { mutableStateOf(initial?.kids ?: false) }
    var pinHash by remember { mutableStateOf(initial?.pinHash) }
    var settingPin by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val id = remember { initial?.id ?: java.util.UUID.randomUUID().toString() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
            Column(Modifier.padding(20.dp).widthIn(max = 520.dp)) {
                Text(if (initial == null) "Novo perfil" else "Editar perfil", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(avatar, name, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(Profile.MAX_NAME) },
                        label = { Text("Nome") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Escolha um avatar", color = Color(0xFFB8C0CC), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(58.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 220.dp),
                ) {
                    items(if (kids) Avatars.KIDS + Avatars.ADULT else Avatars.ALL) { key ->
                        ProfileAvatar(key, key, 58.dp, Modifier.clickable { avatar = key }, selected = key == avatar)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Perfil infantil", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Mostra só animações e títulos para a família", color = Color(0xFF8A94A6), fontSize = 12.sp)
                    }
                    Switch(checked = kids, onCheckedChange = { kids = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("PIN do perfil", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(if (pinHash != null) "Ativado — pede 4 dígitos para entrar" else "Desativado", color = Color(0xFF8A94A6), fontSize = 12.sp)
                    }
                    TextButton(onClick = { if (pinHash != null) pinHash = null else settingPin = true }) {
                        Text(if (pinHash != null) "Remover" else "Definir", color = StreamFlixColors.Accent)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canDelete) TextButton(onClick = { confirmDelete = true }) { Text("Excluir", color = Color(0xFFFF6B6B)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFB8C0CC)) }
                    Button(
                        onClick = { onSave(Profile(id, name.ifBlank { "Perfil" }, avatar, kids, pinHash)) },
                        colors = ButtonDefaults.buttonColors(containerColor = StreamFlixColors.Accent),
                    ) { Text("Salvar", color = StreamFlixColors.OnAccent) }
                }
            }
        }
    }
    if (settingPin) {
        PinDialog(title = "Crie um PIN de 4 dígitos", onDismiss = { settingPin = false }) { pin ->
            pinHash = ProfileStore.hashPin(id, pin); settingPin = false; true
        }
    }
    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir ${initial.name}?") },
            text = { Text("O perfil sai deste aparelho e da sua conta.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(initial.id) }) { Text("Excluir", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Pede 4 dígitos. [onSubmit] devolve false para "PIN incorreto". */
@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v ->
                        pin = v.filter { it.isDigit() }.take(4); error = false
                        if (pin.length == 4 && !onSubmit(pin)) { error = true; pin = "" }
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error,
                )
                if (error) Text("PIN incorreto", color = Color(0xFFFF6B6B), fontSize = 12.sp)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
