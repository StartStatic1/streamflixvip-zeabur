-if class com.streamflixvip.tv.network.LiveStreamOption
-keepnames class com.streamflixvip.tv.network.LiveStreamOption
-if class com.streamflixvip.tv.network.LiveStreamOption
-keep class com.streamflixvip.tv.network.LiveStreamOptionJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.LiveStreamOption
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.LiveStreamOption
-keepclassmembers class com.streamflixvip.tv.network.LiveStreamOption {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.Integer,java.lang.String,java.lang.Boolean,java.lang.Boolean,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
