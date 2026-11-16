-if class com.streamflixvip.app.network.LiveEpgResponse
-keepnames class com.streamflixvip.app.network.LiveEpgResponse
-if class com.streamflixvip.app.network.LiveEpgResponse
-keep class com.streamflixvip.app.network.LiveEpgResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.LiveEpgResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.LiveEpgResponse
-keepclassmembers class com.streamflixvip.app.network.LiveEpgResponse {
    public synthetic <init>(java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
