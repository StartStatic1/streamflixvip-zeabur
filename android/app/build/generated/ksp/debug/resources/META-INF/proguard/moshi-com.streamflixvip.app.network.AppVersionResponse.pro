-if class com.streamflixvip.app.network.AppVersionResponse
-keepnames class com.streamflixvip.app.network.AppVersionResponse
-if class com.streamflixvip.app.network.AppVersionResponse
-keep class com.streamflixvip.app.network.AppVersionResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.AppVersionResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.AppVersionResponse
-keepclassmembers class com.streamflixvip.app.network.AppVersionResponse {
    public synthetic <init>(int,java.lang.String,java.lang.String,boolean,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
