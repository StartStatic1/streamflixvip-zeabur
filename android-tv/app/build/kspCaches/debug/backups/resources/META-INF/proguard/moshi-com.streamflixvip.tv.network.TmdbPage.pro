-if class com.streamflixvip.tv.network.TmdbPage
-keepnames class com.streamflixvip.tv.network.TmdbPage
-if class com.streamflixvip.tv.network.TmdbPage
-keep class com.streamflixvip.tv.network.TmdbPageJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.TmdbPage
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.TmdbPage
-keepclassmembers class com.streamflixvip.tv.network.TmdbPage {
    public synthetic <init>(java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
