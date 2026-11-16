-if class com.streamflixvip.app.network.SourceMeta
-keepnames class com.streamflixvip.app.network.SourceMeta
-if class com.streamflixvip.app.network.SourceMeta
-keep class com.streamflixvip.app.network.SourceMetaJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.SourceMeta
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.SourceMeta
-keepclassmembers class com.streamflixvip.app.network.SourceMeta {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
