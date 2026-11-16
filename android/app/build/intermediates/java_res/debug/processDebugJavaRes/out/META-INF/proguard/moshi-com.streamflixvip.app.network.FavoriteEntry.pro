-if class com.streamflixvip.app.network.FavoriteEntry
-keepnames class com.streamflixvip.app.network.FavoriteEntry
-if class com.streamflixvip.app.network.FavoriteEntry
-keep class com.streamflixvip.app.network.FavoriteEntryJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.FavoriteEntry
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.FavoriteEntry
-keepclassmembers class com.streamflixvip.app.network.FavoriteEntry {
    public synthetic <init>(int,java.lang.String,java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
