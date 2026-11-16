-if class com.streamflixvip.app.network.TmdbEpisode
-keepnames class com.streamflixvip.app.network.TmdbEpisode
-if class com.streamflixvip.app.network.TmdbEpisode
-keep class com.streamflixvip.app.network.TmdbEpisodeJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.TmdbEpisode
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.TmdbEpisode
-keepclassmembers class com.streamflixvip.app.network.TmdbEpisode {
    public synthetic <init>(int,java.lang.String,java.lang.String,java.lang.String,java.lang.Integer,java.lang.String,java.lang.Double,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
