-if class com.streamflixvip.app.network.ReelListResponse
-keepnames class com.streamflixvip.app.network.ReelListResponse
-if class com.streamflixvip.app.network.ReelListResponse
-keep class com.streamflixvip.app.network.ReelListResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.ReelListResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.ReelListResponse
-keepclassmembers class com.streamflixvip.app.network.ReelListResponse {
    public synthetic <init>(java.util.List,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
