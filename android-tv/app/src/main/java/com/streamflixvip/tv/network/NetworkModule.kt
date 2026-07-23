package com.streamflixvip.tv.network

import com.squareup.moshi.Moshi
import com.streamflixvip.tv.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Ponto único de construção dos clientes de rede. Sem injeção de
 * dependência (Hilt/Koin) de propósito nesta primeira versão — objeto
 * singleton simples é suficiente pro tamanho atual do app, e evita
 * complexidade extra de configuração de build numa primeira entrega.
 * Se o app crescer bastante, migrar pra Hilt depois é direto.
 */
object NetworkModule {

    /**
     * Domínio alternativo do backend, hospedado no Zeabur — mesmo
     * server.js do Koyeb (BuildConfig.API_BASE_URL), rodando em paralelo
     * numa plataforma diferente. Existe porque alguns provedores de
     * vídeo falhavam de forma diferente em cada plataforma (timeout numa,
     * erro de CORS na outra); em vez de escolher uma só e ficar refém
     * dela, o player faz os dois tentarem ao mesmo tempo e usa o que
     * responder primeiro (ver StreamUrlResolver).
     */
    const val ZEABUR_BASE_URL = "https://www.streamflixvip.online/"

    /**
     * Preenchido pelo MainActivity assim que o Context da Activity está
     * disponível — precisa existir ANTES de qualquer chamada autenticada
     * ser feita, porque authenticator (abaixo) depende dele pra ler e
     * salvar o refresh_token. Nulo só nos instantes antes do app montar
     * a UI, quando nenhuma chamada autenticada ainda teria sido disparada.
     */
    var sessionStore: com.streamflixvip.tv.data.SessionStore? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE // nunca loga corpo de requisição em build de release
        }
    }

    /**
     * Renova o access_token automaticamente sempre que uma chamada volta
     * com 401 — o JWT do Supabase expira (padrão: 1h), e sem isso toda
     * chamada autenticada (favoritar, checar progresso, etc) passa a
     * falhar silenciosamente pela RLS depois desse tempo: o Supabase não
     * retorna erro óbvio, só age como se a pessoa não tivesse permissão,
     * o que na prática parecia "não salvou nada" — esse era o bug do
     * coração/favoritos não persistindo depois de um tempo de uso.
     *
     * authenticate() do OkHttp já existe pra exatamente esse cenário:
     * roda de forma síncrona quando o servidor responde 401, antes de a
     * chamada original ser considerada "falhada" — se retornar uma
     * nova Request com o header atualizado, o OkHttp repete a chamada
     * automaticamente com esse novo header, de forma transparente pra
     * quem chamou (o ViewModel nem sabe que um refresh aconteceu).
     */
    private val tokenAuthenticator = okhttp3.Authenticator { _, response ->
        // Evita loop infinito: se a MESMA request já tentou renovar antes
        // (marcada via header interno) e voltou 401 de novo, desiste —
        // token de fato inválido/revogado, não adianta tentar de novo.
        if (response.request.header("X-Retry-After-Refresh") != null) {
            return@Authenticator null
        }
        val store = sessionStore ?: return@Authenticator null
        val refreshToken = store.refreshToken ?: return@Authenticator null

        val newSession = try {
            kotlinx.coroutines.runBlocking {
                supabaseAuthApi.refreshToken(
                    apiKey = supabaseAnonKey,
                    body = RefreshTokenRequest(refreshToken),
                )
            }
        } catch (_: Exception) {
            null
        } ?: return@Authenticator null

        store.updateTokens(newSession.access_token, newSession.refresh_token)

        response.request.newBuilder()
            .header("Authorization", "Bearer ${newSession.access_token}")
            .header("X-Retry-After-Refresh", "true")
            .build()
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Client com timeout bem mais curto, usado só pela race de stream
     * (StreamUrlResolver) — o objetivo ali é descobrir RÁPIDO qual dos
     * dois backends está saudável, não esperar o timeout longo padrão
     * duas vezes em sequência antes de desistir.
     */
    val fastProbeClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // Sem KotlinJsonAdapterFactory (reflection) — todas as data classes de
    // rede estão anotadas com @JsonClass(generateAdapter = true), então o
    // Moshi encontra os adapters gerados em tempo de compilação (KSP).
    private val moshi = Moshi.Builder()
        .build()

    val tmdbApi: TmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL) // mesmo domínio Koyeb do site
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbApi::class.java)
    }

    val vipApi: VipApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL) // mesmo backend Express, endpoints /api/vip-status e /api/redeem-vip
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(VipApi::class.java)
    }

    val supabaseApi: SupabaseApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseApi::class.java)
    }

    val watchProgressApi: WatchProgressApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WatchProgressApi::class.java)
    }

    val commentsApi: CommentsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CommentsApi::class.java)
    }

    /**
     * Checagem de versão/update obrigatório — usa o MESMO backend Express
     * (api/app-version.js) do resto do app, não uma URL separada, pra não
     * precisar gerenciar mais um domínio/deploy só pra isso.
     */
    val appVersionApi: AppVersionApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AppVersionApi::class.java)
    }

    val favoritesApi: FavoritesApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FavoritesApi::class.java)
    }

    val supabaseAuthApi: SupabaseAuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseAuthApi::class.java)
    }

    /** Anon key pública do Supabase — mesmo valor embutido em Public/index.html. */
    val supabaseAnonKey: String get() = BuildConfig.SUPABASE_ANON_KEY
}
