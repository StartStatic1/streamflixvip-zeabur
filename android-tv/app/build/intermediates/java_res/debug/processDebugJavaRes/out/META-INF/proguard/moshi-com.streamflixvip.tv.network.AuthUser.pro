-if class com.streamflixvip.tv.network.AuthUser
-keepnames class com.streamflixvip.tv.network.AuthUser
-if class com.streamflixvip.tv.network.AuthUser
-keep class com.streamflixvip.tv.network.AuthUserJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
