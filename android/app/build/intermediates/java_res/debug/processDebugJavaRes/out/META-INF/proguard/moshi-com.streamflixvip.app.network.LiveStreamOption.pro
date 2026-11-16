-if class com.streamflixvip.app.network.LiveStreamOption
-keepnames class com.streamflixvip.app.network.LiveStreamOption
-if class com.streamflixvip.app.network.LiveStreamOption
-keep class com.streamflixvip.app.network.LiveStreamOptionJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.LiveStreamOption
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.LiveStreamOption
-keepclassmembers class com.streamflixvip.app.network.LiveStreamOption {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.Integer,java.lang.String,java.lang.Boolean,java.lang.Boolean,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
