# ============================================================
# Regras de ofuscação/minificação pro build de release.
# ============================================================

# ─── Media3/ExoPlayer ──────────────────────────────────────
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ─── Lifecycle / Navigation / ViewModel ────────────────────
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.compose.** { *; }
-dontwarn androidx.lifecycle.**
-dontwarn androidx.navigation.**
-dontwarn androidx.compose.**

# ─── Moshi (com codegen KSP) ───────────────────────────────
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-dontwarn com.squareup.moshi.**
-dontwarn kotlin.reflect.**

# ─── Retrofit ──────────────────────────────────────────────
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn retrofit2.**

# ─── OkHttp ────────────────────────────────────────────────
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ─── kotlinx.coroutines ────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.CoroutineDispatcher {}
-keep class kotlinx.coroutines.internal.DispatchedTask { *; }
-dontwarn kotlinx.coroutines.**

# ─── Kotlin ────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.collections.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.**

# ─── Coil ──────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ─── WebView ───────────────────────────────────────────────
-keep class androidx.webkit.** { *; }
-dontwarn androidx.webkit.**

# ─── Material Components ───────────────────────────────────
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ─── Splash Screen ─────────────────────────────────────────
-keep class androidx.core.splashscreen.** { *; }

# ─── Desugar JDK libs (java.time) ──────────────────────────
-keep class j$.time.** { *; }
-keep class j$.util.** { *; }

# ─── Pacotes do app (garante que nada do app seja removido) ──
-keep class com.streamflixvip.tv.** { *; }

# ─── Compose for TV ────────────────────────────────────────
-keep class androidx.tv.** { *; }
-dontwarn androidx.tv.**
