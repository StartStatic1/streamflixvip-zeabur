-if class com.streamflixvip.app.network.TmdbItem
-keepnames class com.streamflixvip.app.network.TmdbItem
-if class com.streamflixvip.app.network.TmdbItem
-keep class com.streamflixvip.app.network.TmdbItemJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.TmdbItem
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.TmdbItem
-keepclassmembers class com.streamflixvip.app.network.TmdbItem {
    public synthetic <init>(int,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.Double,java.lang.String,java.lang.Double,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
