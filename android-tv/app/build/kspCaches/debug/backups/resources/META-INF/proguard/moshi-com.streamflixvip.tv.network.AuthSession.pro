-if class com.streamflixvip.tv.network.AuthSession
-keepnames class com.streamflixvip.tv.network.AuthSession
-if class com.streamflixvip.tv.network.AuthSession
-keep class com.streamflixvip.tv.network.AuthSessionJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
