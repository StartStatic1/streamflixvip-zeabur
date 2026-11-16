-if class com.streamflixvip.tv.network.AppVersionResponse
-keepnames class com.streamflixvip.tv.network.AppVersionResponse
-if class com.streamflixvip.tv.network.AppVersionResponse
-keep class com.streamflixvip.tv.network.AppVersionResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.AppVersionResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.AppVersionResponse
-keepclassmembers class com.streamflixvip.tv.network.AppVersionResponse {
    public synthetic <init>(int,java.lang.String,java.lang.String,boolean,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
