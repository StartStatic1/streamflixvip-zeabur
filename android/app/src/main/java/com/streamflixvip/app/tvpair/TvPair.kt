package com.streamflixvip.app.tvpair

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.streamflixvip.app.BuildConfig
import com.streamflixvip.app.data.AuthTokenHelper
import com.streamflixvip.app.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Pedido para conectar uma TV (vindo do QR code ou do menu Perfil). */
object TvPair {
    /** pairId vindo do link streamflixvip://tvpair?p=... ; "" = abrir para digitar o código. */
    val pending = MutableStateFlow<String?>(null)

    fun handleUri(uri: android.net.Uri?) {
        runCatching {
            if (uri?.scheme == "streamflixvip" && uri.host == "tvpair") {
                val p = uri.getQueryParameter("p").orEmpty().filter { it.isLetterOrDigit() }.take(64)
                pending.value = p
            }
        }
    }

    /** null = sucesso; senão a mensagem de erro em português. */
    suspend fun confirm(pairId: String?, pin: String?): String? = withContext(Dispatchers.IO) {
        runCatching {
            val token = AuthTokenHelper.validAccessToken() ?: return@runCatching "Entre na sua conta no app primeiro."
            val body = JSONObject().put("action", "confirm")
            if (!pairId.isNullOrBlank()) body.put("pairId", pairId) else body.put("pin", pin.orEmpty())
            val req = Request.Builder()
                .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/tv-pair")
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            NetworkModule.okHttpClient.newCall(req).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (r.isSuccessful) null
                else runCatching { JSONObject(txt).optString("error") }.getOrNull()?.ifBlank { null }
                    ?: if (r.code == 404) "Código expirado ou recurso ainda indisponível." else "Não foi possível conectar a TV."
            }
        }.getOrElse { "Sem conexão. Tente de novo." }
    }
}

@Composable
fun TvPairDialogHost() {
    val pending by TvPair.pending.collectAsState()
    val pairId = pending ?: return
    var pin by remember(pairId) { mutableStateOf("") }
    var busy by remember(pairId) { mutableStateOf(false) }
    var msg by remember(pairId) { mutableStateOf<String?>(null) }
    var done by remember(pairId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val needsPin = pairId.isBlank()

    AlertDialog(
        onDismissRequest = { if (!busy) TvPair.pending.value = null },
        title = { Text(if (done) "TV conectada! ✅" else "Conectar TV") },
        text = {
            Column {
                when {
                    done -> Text("Pronto! Em alguns segundos a TV entra sozinha.")
                    needsPin -> {
                        Text("Digite o código de 6 números que aparece na tela da TV.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { v -> pin = v.filter { it.isDigit() }.take(6) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            label = { Text("Código da TV") },
                        )
                    }
                    else -> Text("Liberar a TV com a sua conta VIP?")
                }
                if (busy) { Spacer(Modifier.height(12.dp)); CircularProgressIndicator() }
                msg?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (done) {
                TextButton(onClick = { TvPair.pending.value = null }) { Text("Fechar") }
            } else {
                TextButton(
                    enabled = !busy && (!needsPin || pin.length == 6),
                    onClick = {
                        busy = true; msg = null
                        scope.launch {
                            val err = TvPair.confirm(pairId.ifBlank { null }, pin)
                            busy = false
                            if (err == null) done = true else msg = err
                        }
                    },
                ) { Text("Conectar") }
            }
        },
        dismissButton = {
            if (!done) TextButton(enabled = !busy, onClick = { TvPair.pending.value = null }) { Text("Cancelar") }
        },
    )
}
