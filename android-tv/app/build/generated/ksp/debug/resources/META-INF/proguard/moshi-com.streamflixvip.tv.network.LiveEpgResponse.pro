-if class com.streamflixvip.tv.network.LiveEpgResponse
-keepnames class com.streamflixvip.tv.network.LiveEpgResponse
-if class com.streamflixvip.tv.network.LiveEpgResponse
-keep class com.streamflixvip.tv.network.LiveEpgResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.LiveEpgResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.LiveEpgResponse
-keepclassmembers class com.streamflixvip.tv.network.LiveEpgResponse {
    public synthetic <init>(java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
