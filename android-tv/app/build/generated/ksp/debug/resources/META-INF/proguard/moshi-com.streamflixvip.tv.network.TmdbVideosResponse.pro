-if class com.streamflixvip.tv.network.TmdbVideosResponse
-keepnames class com.streamflixvip.tv.network.TmdbVideosResponse
-if class com.streamflixvip.tv.network.TmdbVideosResponse
-keep class com.streamflixvip.tv.network.TmdbVideosResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.TmdbVideosResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.TmdbVideosResponse
-keepclassmembers class com.streamflixvip.tv.network.TmdbVideosResponse {
    public synthetic <init>(java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
