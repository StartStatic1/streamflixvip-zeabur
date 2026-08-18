# StreamFlixVIP — ProGuard / R8 (release)
# Objetivo: ofuscar logica do app sem quebrar Retrofit/Moshi/Compose/ExoPlayer.
# A protecao real de VIP continua no servidor (REQUIRE_AUTH_MEDIA / REQUIRE_VIP_LIVE_TV).

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes AnnotationDefault

# Entrypoints
-keep class com.streamflixvip.app.StreamFlixApp { *; }
-keep class com.streamflixvip.app.MainActivity { *; }

# Moshi codegen
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-dontwarn com.squareup.moshi.**
-dontwarn kotlin.reflect.**

# Retrofit
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class retrofit2.** { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn retrofit2.**

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Compose / Lifecycle
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**
-dontwarn androidx.navigation.**
-keep class androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.CoroutineDispatcher {}
-keep class kotlinx.coroutines.internal.DispatchedTask { *; }
-dontwarn kotlinx.coroutines.**

# Coil / WebView / Material
-keep class coil.** { *; }
-dontwarn coil.**
-keep class androidx.webkit.** { *; }
-dontwarn androidx.webkit.**
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-keep class androidx.core.splashscreen.** { *; }

# Desugar
-keep class j$.time.** { *; }
-keep class j$.util.** { *; }

-dontwarn kotlin.**
-dontwarn kotlin.reflect.**

# Strip Log.* em release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# NAO manter -keep class com.streamflixvip.app.** { *; }
# Isso impedia ofuscar a logica do app.

# AdMob / GMS
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
