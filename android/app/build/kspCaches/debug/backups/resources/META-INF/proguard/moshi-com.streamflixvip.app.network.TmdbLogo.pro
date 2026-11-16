-if class com.streamflixvip.app.network.TmdbLogo
-keepnames class com.streamflixvip.app.network.TmdbLogo
-if class com.streamflixvip.app.network.TmdbLogo
-keep class com.streamflixvip.app.network.TmdbLogoJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.TmdbLogo
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.TmdbLogo
-keepclassmembers class com.streamflixvip.app.network.TmdbLogo {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.Double,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
