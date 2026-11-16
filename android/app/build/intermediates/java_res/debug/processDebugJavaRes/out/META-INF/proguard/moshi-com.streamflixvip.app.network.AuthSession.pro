-if class com.streamflixvip.app.network.AuthSession
-keepnames class com.streamflixvip.app.network.AuthSession
-if class com.streamflixvip.app.network.AuthSession
-keep class com.streamflixvip.app.network.AuthSessionJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
