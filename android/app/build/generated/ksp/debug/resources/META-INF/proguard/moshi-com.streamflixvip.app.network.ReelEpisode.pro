-if class com.streamflixvip.app.network.ReelEpisode
-keepnames class com.streamflixvip.app.network.ReelEpisode
-if class com.streamflixvip.app.network.ReelEpisode
-keep class com.streamflixvip.app.network.ReelEpisodeJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.ReelEpisode
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.ReelEpisode
-keepclassmembers class com.streamflixvip.app.network.ReelEpisode {
    public synthetic <init>(java.lang.String,java.lang.Integer,java.lang.String,java.lang.String,java.lang.Integer,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
