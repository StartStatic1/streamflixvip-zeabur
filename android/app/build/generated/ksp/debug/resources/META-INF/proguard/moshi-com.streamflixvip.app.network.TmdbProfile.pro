-if class com.streamflixvip.app.network.TmdbProfile
-keepnames class com.streamflixvip.app.network.TmdbProfile
-if class com.streamflixvip.app.network.TmdbProfile
-keep class com.streamflixvip.app.network.TmdbProfileJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.TmdbProfile
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.TmdbProfile
-keepclassmembers class com.streamflixvip.app.network.TmdbProfile {
    public synthetic <init>(java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
