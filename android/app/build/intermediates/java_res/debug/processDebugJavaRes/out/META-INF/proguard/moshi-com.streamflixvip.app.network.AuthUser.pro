-if class com.streamflixvip.app.network.AuthUser
-keepnames class com.streamflixvip.app.network.AuthUser
-if class com.streamflixvip.app.network.AuthUser
-keep class com.streamflixvip.app.network.AuthUserJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
