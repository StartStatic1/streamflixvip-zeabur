-if class com.streamflixvip.tv.network.VipSource
-keepnames class com.streamflixvip.tv.network.VipSource
-if class com.streamflixvip.tv.network.VipSource
-keep class com.streamflixvip.tv.network.VipSourceJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.VipSource
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.VipSource
-keepclassmembers class com.streamflixvip.tv.network.VipSource {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.Integer,java.lang.Boolean,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
