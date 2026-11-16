-if class com.streamflixvip.tv.network.TvStatusRequest
-keepnames class com.streamflixvip.tv.network.TvStatusRequest
-if class com.streamflixvip.tv.network.TvStatusRequest
-keep class com.streamflixvip.tv.network.TvStatusRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.TvStatusRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.TvStatusRequest
-keepclassmembers class com.streamflixvip.tv.network.TvStatusRequest {
    public synthetic <init>(java.lang.String,boolean,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
