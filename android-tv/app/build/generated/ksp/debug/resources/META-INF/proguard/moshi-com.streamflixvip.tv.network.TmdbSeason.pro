-if class com.streamflixvip.tv.network.TmdbSeason
-keepnames class com.streamflixvip.tv.network.TmdbSeason
-if class com.streamflixvip.tv.network.TmdbSeason
-keep class com.streamflixvip.tv.network.TmdbSeasonJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.TmdbSeason
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.TmdbSeason
-keepclassmembers class com.streamflixvip.tv.network.TmdbSeason {
    public synthetic <init>(int,int,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
