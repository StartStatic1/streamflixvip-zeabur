-if class com.streamflixvip.tv.network.RelayAddon
-keepnames class com.streamflixvip.tv.network.RelayAddon
-if class com.streamflixvip.tv.network.RelayAddon
-keep class com.streamflixvip.tv.network.RelayAddonJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.RelayAddon
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.RelayAddon
-keepclassmembers class com.streamflixvip.tv.network.RelayAddon {
    public synthetic <init>(java.lang.String,java.lang.String,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
