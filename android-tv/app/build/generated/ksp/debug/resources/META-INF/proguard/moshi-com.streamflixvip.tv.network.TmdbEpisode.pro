-if class com.streamflixvip.tv.network.TmdbEpisode
-keepnames class com.streamflixvip.tv.network.TmdbEpisode
-if class com.streamflixvip.tv.network.TmdbEpisode
-keep class com.streamflixvip.tv.network.TmdbEpisodeJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.TmdbEpisode
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.TmdbEpisode
-keepclassmembers class com.streamflixvip.tv.network.TmdbEpisode {
    public synthetic <init>(int,java.lang.String,java.lang.String,java.lang.String,java.lang.Integer,java.lang.String,java.lang.Double,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
