-if class com.streamflixvip.app.network.VipSource
-keepnames class com.streamflixvip.app.network.VipSource
-if class com.streamflixvip.app.network.VipSource
-keep class com.streamflixvip.app.network.VipSourceJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.VipSource
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.VipSource
-keepclassmembers class com.streamflixvip.app.network.VipSource {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.Integer,com.streamflixvip.app.network.SourceMeta,java.lang.Boolean,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
