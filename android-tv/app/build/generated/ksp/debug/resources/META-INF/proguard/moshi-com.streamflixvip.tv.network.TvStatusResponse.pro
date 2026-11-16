-if class com.streamflixvip.tv.network.TvStatusResponse
-keepnames class com.streamflixvip.tv.network.TvStatusResponse
-if class com.streamflixvip.tv.network.TvStatusResponse
-keep class com.streamflixvip.tv.network.TvStatusResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.TvStatusResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.TvStatusResponse
-keepclassmembers class com.streamflixvip.tv.network.TvStatusResponse {
    public synthetic <init>(boolean,java.lang.String,java.lang.String,boolean,java.lang.String,java.lang.String,java.lang.Integer,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
