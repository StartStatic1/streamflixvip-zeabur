package com.streamflixvip.tv.network

import com.squareup.moshi.Moshi
import com.streamflixvip.tv.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    const val ZEABUR_BASE_URL = "https://www.streamflixvip.online/"

    var sessionStore: com.streamflixvip.tv.data.SessionStore? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
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
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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
