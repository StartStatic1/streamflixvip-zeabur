-if class com.streamflixvip.tv.network.SendOtpRequest
-keepnames class com.streamflixvip.tv.network.SendOtpRequest
-if class com.streamflixvip.tv.network.SendOtpRequest
-keep class com.streamflixvip.tv.network.SendOtpRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.SendOtpRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.SendOtpRequest
-keepclassmembers class com.streamflixvip.tv.network.SendOtpRequest {
    public synthetic <init>(java.lang.String,boolean,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
