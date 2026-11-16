-if class com.streamflixvip.tv.network.PixResponse
-keepnames class com.streamflixvip.tv.network.PixResponse
-if class com.streamflixvip.tv.network.PixResponse
-keep class com.streamflixvip.tv.network.PixResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.PixResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.PixResponse
-keepclassmembers class com.streamflixvip.tv.network.PixResponse {
    public synthetic <init>(boolean,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
