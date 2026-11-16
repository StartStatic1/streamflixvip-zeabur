-if class com.streamflixvip.app.network.LiveChannel
-keepnames class com.streamflixvip.app.network.LiveChannel
-if class com.streamflixvip.app.network.LiveChannel
-keep class com.streamflixvip.app.network.LiveChannelJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.LiveChannel
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.LiveChannel
-keepclassmembers class com.streamflixvip.app.network.LiveChannel {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
