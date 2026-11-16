-if class com.streamflixvip.app.network.LiveTvResponse
-keepnames class com.streamflixvip.app.network.LiveTvResponse
-if class com.streamflixvip.app.network.LiveTvResponse
-keep class com.streamflixvip.app.network.LiveTvResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.LiveTvResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.LiveTvResponse
-keepclassmembers class com.streamflixvip.app.network.LiveTvResponse {
    public synthetic <init>(java.util.List,java.util.List,int,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
