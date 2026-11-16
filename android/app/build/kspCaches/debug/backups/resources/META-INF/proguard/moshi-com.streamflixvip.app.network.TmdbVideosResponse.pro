-if class com.streamflixvip.app.network.TmdbVideosResponse
-keepnames class com.streamflixvip.app.network.TmdbVideosResponse
-if class com.streamflixvip.app.network.TmdbVideosResponse
-keep class com.streamflixvip.app.network.TmdbVideosResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.TmdbVideosResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.TmdbVideosResponse
-keepclassmembers class com.streamflixvip.app.network.TmdbVideosResponse {
    public synthetic <init>(java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
