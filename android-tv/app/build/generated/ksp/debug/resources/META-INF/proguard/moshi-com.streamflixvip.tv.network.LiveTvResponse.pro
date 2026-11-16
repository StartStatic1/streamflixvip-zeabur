-if class com.streamflixvip.tv.network.LiveTvResponse
-keepnames class com.streamflixvip.tv.network.LiveTvResponse
-if class com.streamflixvip.tv.network.LiveTvResponse
-keep class com.streamflixvip.tv.network.LiveTvResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.LiveTvResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.LiveTvResponse
-keepclassmembers class com.streamflixvip.tv.network.LiveTvResponse {
    public synthetic <init>(java.util.List,java.util.List,int,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
