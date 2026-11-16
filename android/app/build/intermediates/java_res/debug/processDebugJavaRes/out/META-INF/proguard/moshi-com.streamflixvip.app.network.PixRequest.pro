-if class com.streamflixvip.app.network.PixRequest
-keepnames class com.streamflixvip.app.network.PixRequest
-if class com.streamflixvip.app.network.PixRequest
-keep class com.streamflixvip.app.network.PixRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.PixRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.PixRequest
-keepclassmembers class com.streamflixvip.app.network.PixRequest {
    public synthetic <init>(java.lang.String,double,java.lang.String,int,java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
