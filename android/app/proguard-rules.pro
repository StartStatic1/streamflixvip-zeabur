# Regras de ofuscação/minificação pro build de release.
#
# Media3/ExoPlayer: o player guarda estado interno (posição, faixas,
# comandos de sessão) em objetos que o Android tenta serializar/restaurar
# via Bundle nos Handlers internos dele (Looper/Handler.handleCallback).
# As regras consumer-proguard publicadas pelo Media3 1.3.1 não cobrem
# 100% dos casos quando combinadas com Compose Navigation — o R8 pode
# deixar uma classe interna como "abstract" (removendo a única
# implementação concreta que ele achou não-referenciada estaticamente),
# e aí a tentativa de desserializar aquele Bundle quebra em runtime com
# "Cannot serialize abstract class". As regras -keep abaixo impedem o R8
# de mexer nessas classes internas.
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Mesma categoria de problema pode acontecer com o estado salvo do
# Compose Navigation / ViewModel (SavedStateHandle), que também serializa
# via Bundle nos callbacks do Looper.
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }
-dontwarn androidx.lifecycle.**
-dontwarn androidx.navigation.**

# Moshi (usado pelo Retrofit) desserializa data classes via reflection —
# sem isso, os data class de modelo (respostas da API) podem perder
# campos ou o construtor esperado depois de minificado.
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-dontwarn com.squareup.moshi.**
