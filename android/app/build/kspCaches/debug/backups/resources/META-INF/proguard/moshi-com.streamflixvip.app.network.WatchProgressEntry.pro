-if class com.streamflixvip.app.network.WatchProgressEntry
-keepnames class com.streamflixvip.app.network.WatchProgressEntry
-if class com.streamflixvip.app.network.WatchProgressEntry
-keep class com.streamflixvip.app.network.WatchProgressEntryJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
