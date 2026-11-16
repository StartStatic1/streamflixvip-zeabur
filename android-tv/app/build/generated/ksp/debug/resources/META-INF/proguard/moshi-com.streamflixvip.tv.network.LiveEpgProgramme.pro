-if class com.streamflixvip.tv.network.LiveEpgProgramme
-keepnames class com.streamflixvip.tv.network.LiveEpgProgramme
-if class com.streamflixvip.tv.network.LiveEpgProgramme
-keep class com.streamflixvip.tv.network.LiveEpgProgrammeJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.LiveEpgProgramme
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.LiveEpgProgramme
-keepclassmembers class com.streamflixvip.tv.network.LiveEpgProgramme {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
