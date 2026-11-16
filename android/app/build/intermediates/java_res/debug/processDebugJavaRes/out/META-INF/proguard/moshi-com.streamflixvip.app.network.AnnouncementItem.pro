-if class com.streamflixvip.app.network.AnnouncementItem
-keepnames class com.streamflixvip.app.network.AnnouncementItem
-if class com.streamflixvip.app.network.AnnouncementItem
-keep class com.streamflixvip.app.network.AnnouncementItemJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.AnnouncementItem
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.AnnouncementItem
-keepclassmembers class com.streamflixvip.app.network.AnnouncementItem {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.Integer,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
