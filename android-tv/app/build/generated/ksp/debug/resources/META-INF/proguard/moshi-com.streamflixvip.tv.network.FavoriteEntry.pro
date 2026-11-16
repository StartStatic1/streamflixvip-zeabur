-if class com.streamflixvip.tv.network.FavoriteEntry
-keepnames class com.streamflixvip.tv.network.FavoriteEntry
-if class com.streamflixvip.tv.network.FavoriteEntry
-keep class com.streamflixvip.tv.network.FavoriteEntryJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.FavoriteEntry
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.FavoriteEntry
-keepclassmembers class com.streamflixvip.tv.network.FavoriteEntry {
    public synthetic <init>(int,java.lang.String,java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
