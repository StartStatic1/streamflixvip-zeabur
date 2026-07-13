package com.streamflixvip.app.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.streamflixvip.app.BuildConfig
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

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE // nunca loga corpo de requisição em build de release
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val tmdbApi: TmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL) // mesmo domínio Koyeb do site
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbApi::class.java)
    }

    val supabaseApi: SupabaseApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseApi::class.java)
    }

    /** Anon key pública do Supabase — mesmo valor embutido em Public/index.html. */
    val supabaseAnonKey: String get() = BuildConfig.SUPABASE_ANON_KEY
}
