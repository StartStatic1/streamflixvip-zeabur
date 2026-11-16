-if class com.streamflixvip.app.network.LiveEpgProgramme
-keepnames class com.streamflixvip.app.network.LiveEpgProgramme
-if class com.streamflixvip.app.network.LiveEpgProgramme
-keep class com.streamflixvip.app.network.LiveEpgProgrammeJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.LiveEpgProgramme
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.LiveEpgProgramme
-keepclassmembers class com.streamflixvip.app.network.LiveEpgProgramme {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
