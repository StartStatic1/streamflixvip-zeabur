package com.streamflixvip.app.network

import com.squareup.moshi.Moshi
import com.streamflixvip.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    const val ZEABUR_BASE_URL = "https://www.streamflixvip.online/"

    var sessionStore: com.streamflixvip.app.data.SessionStore? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    /**
     * Anexa Authorization + X-User-Id em toda request para o backend Express.
     * Necessário pro gate VIP de /api/live-tv e /api/media-sources.
     */
    private val sessionAuthInterceptor = Interceptor { chain ->
        val store = sessionStore
        val original = chain.request()
        val builder = original.newBuilder()
        val token = store?.accessToken
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        val userId = store?.userId
        if (!userId.isNullOrBlank()) {
            builder.header("X-User-Id", userId)
        }
        chain.proceed(builder.build())
    }

    private val tokenAuthenticator = okhttp3.Authenticator { _, response ->
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
        .addInterceptor(sessionAuthInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val liveTvOkHttp = OkHttpClient.Builder()
        .addInterceptor(sessionAuthInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    val fastProbeClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().build()

    val tmdbApi: TmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbApi::class.java)
    }

    val vipApi: VipApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(VipApi::class.java)
    }

    val liveTvApi: LiveTvApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(liveTvOkHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LiveTvApi::class.java)
    }

    val mediaSourcesApi: MediaSourcesApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MediaSourcesApi::class.java)
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

    val appVersionApi: AppVersionApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AppVersionApi::class.java)
    }

    val announcementsApi: AnnouncementsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AnnouncementsApi::class.java)
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

    val supabaseAnonKey: String get() = BuildConfig.SUPABASE_ANON_KEY
}
