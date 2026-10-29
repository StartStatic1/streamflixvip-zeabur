package com.streamflixvip.tv.profiles

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Perfis ("Quem está assistindo?").
 * Fonte da verdade = este aparelho (SharedPreferences). Se a tabela `user_profiles` existir no
 * Supabase, a lista também é sincronizada (ProfileSync) — se não existir, tudo continua
 * funcionando só localmente. Nada aqui pode derrubar o app: qualquer JSON ruim vira lista padrão.
 */
data class Profile(
    val id: String,
    val name: String,
    val avatar: String,
    val kids: Boolean = false,
    /** SHA-256(id + ":" + pin), ou null quando sem PIN. */
    val pinHash: String? = null,
) {
    val hasPin: Boolean get() = !pinHash.isNullOrBlank()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("avatar", avatar).put("kids", kids)
        .apply { pinHash?.let { put("pin", it) } }

    companion object {
        fun fromJson(o: JSONObject): Profile? = runCatching {
            Profile(
                id = o.getString("id"),
                name = o.optString("name").ifBlank { "Perfil" }.take(MAX_NAME),
                avatar = Avatars.normalize(o.optString("avatar")),
                kids = o.optBoolean("kids", false),
                pinHash = o.optString("pin").takeIf { it.isNotBlank() },
            )
        }.getOrNull()

        const val MAX_NAME = 20
    }
}

object Avatars {
    /** Ilustrações originais (arquétipos de cinema), em res/drawable-nodpi/av_*.webp */
    val ADULT = listOf(
        "detetive", "astronauta", "cowboy", "vampiro", "pirata", "robo", "mago", "piloto",
        "samurai", "zumbi", "cientista", "cavaleiro", "ninja", "exploradora", "alien", "diretor",
        "mimico", "agente", "lobisomem", "viking", "bailarina", "hacker", "fantasma", "elfa",
        "roqueiro", "mergulhador",
    )
    val KIDS = listOf("dragao", "raposa", "panda", "gato_mago", "dino", "pinguim", "coruja", "coelho_heroi", "polvo")
    val ALL = ADULT + KIDS
    fun normalize(key: String?): String = key?.takeIf { it in ALL } ?: "panda"

    /** Id do drawable; 0 se não achar (a UI cai para a inicial do nome). */
    fun resId(context: Context, key: String): Int = runCatching {
        context.resources.getIdentifier("av_" + normalize(key), "drawable", context.packageName)
    }.getOrDefault(0)
}

object ProfileStore {
    private const val PREFS = "profiles"
    private var prefs: SharedPreferences? = null
    private var scope: String = "local"

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles

    private val _active = MutableStateFlow<Profile?>(null)
    val active: StateFlow<Profile?> = _active

    /** Perfil atual é infantil? (usado pelos filtros de catálogo) */
    val isKids: Boolean get() = _active.value?.kids == true

    /** Carrega os perfis da conta [accountId] (ou do aparelho). Cria um perfil padrão se vazio. */
    fun init(context: Context, accountId: String?, defaultName: String?) {
        runCatching {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs = p
            scope = accountId?.takeIf { it.isNotBlank() } ?: "local"
            var list = parse(p.getString("list_$scope", null))
            if (list.isEmpty()) {
                list = listOf(Profile(UUID.randomUUID().toString(), cleanName(defaultName) ?: "Eu", "detetive"))
                persist(list)
            }
            _profiles.value = list
            val lastId = p.getString("active_$scope", null)
            _active.value = list.firstOrNull { it.id == lastId } ?: list.first()
        }.onFailure {
            val fallback = Profile("default", cleanName(defaultName) ?: "Eu", "detetive")
            _profiles.value = listOf(fallback); _active.value = fallback
        }
    }

    /** Precisa mostrar "Quem está assistindo?" ao abrir? Só quando há mais de um perfil. */
    fun shouldAsk(): Boolean = _profiles.value.size > 1

    fun select(profile: Profile) {
        _active.value = profile
        prefs?.edit()?.putString("active_$scope", profile.id)?.apply()
    }

    fun upsert(profile: Profile): Profile {
        val p = profile.copy(name = cleanName(profile.name) ?: "Perfil", avatar = Avatars.normalize(profile.avatar))
        val cur = _profiles.value
        val next = if (cur.any { it.id == p.id }) cur.map { if (it.id == p.id) p else it } else (cur + p).take(MAX_PROFILES)
        _profiles.value = next
        persist(next)
        if (_active.value?.id == p.id) _active.value = p
        return p
    }

    fun delete(id: String) {
        val next = _profiles.value.filterNot { it.id == id }
        if (next.isEmpty()) return // sempre fica ao menos um
        _profiles.value = next
        persist(next)
        if (_active.value?.id == id) select(next.first())
    }

    /** Substitui pela lista vinda da nuvem (sincronização). */
    fun replaceAll(list: List<Profile>) {
        if (list.isEmpty()) return
        _profiles.value = list.take(MAX_PROFILES)
        persist(_profiles.value)
        val a = _active.value
        _active.value = list.firstOrNull { it.id == a?.id } ?: list.first()
    }

    fun newProfile(name: String, avatar: String, kids: Boolean) =
        Profile(UUID.randomUUID().toString(), name, avatar, kids)

    fun hashPin(profileId: String, pin: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest("$profileId:$pin".toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    fun checkPin(profile: Profile, pin: String): Boolean =
        !profile.hasPin || runCatching { hashPin(profile.id, pin) == profile.pinHash }.getOrDefault(false)

    fun toJsonArray(list: List<Profile> = _profiles.value): JSONArray =
        JSONArray().apply { list.forEach { put(it.toJson()) } }

    fun parse(raw: String?): List<Profile> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(Profile::fromJson) }
            .distinctBy { it.id }
    }.getOrDefault(emptyList())

    private fun persist(list: List<Profile>) {
        runCatching { prefs?.edit()?.putString("list_$scope", toJsonArray(list).toString())?.apply() }
    }

    private fun cleanName(n: String?): String? =
        n?.substringBefore('@')?.trim()?.take(Profile.MAX_NAME)?.takeIf { it.isNotBlank() }

    const val MAX_PROFILES = 5
}
