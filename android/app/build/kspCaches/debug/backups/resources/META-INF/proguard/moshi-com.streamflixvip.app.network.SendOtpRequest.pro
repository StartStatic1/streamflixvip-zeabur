-if class com.streamflixvip.app.network.SendOtpRequest
-keepnames class com.streamflixvip.app.network.SendOtpRequest
-if class com.streamflixvip.app.network.SendOtpRequest
-keep class com.streamflixvip.app.network.SendOtpRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.SendOtpRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.SendOtpRequest
-keepclassmembers class com.streamflixvip.app.network.SendOtpRequest {
    public synthetic <init>(java.lang.String,boolean,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
