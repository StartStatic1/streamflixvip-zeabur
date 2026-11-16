-if class com.streamflixvip.tv.network.TmdbCastMember
-keepnames class com.streamflixvip.tv.network.TmdbCastMember
-if class com.streamflixvip.tv.network.TmdbCastMember
-keep class com.streamflixvip.tv.network.TmdbCastMemberJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.tv.network.TmdbCastMember
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.tv.network.TmdbCastMember
-keepclassmembers class com.streamflixvip.tv.network.TmdbCastMember {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
