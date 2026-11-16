-if class com.streamflixvip.app.network.TmdbCastMember
-keepnames class com.streamflixvip.app.network.TmdbCastMember
-if class com.streamflixvip.app.network.TmdbCastMember
-keep class com.streamflixvip.app.network.TmdbCastMemberJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.TmdbCastMember
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.TmdbCastMember
-keepclassmembers class com.streamflixvip.app.network.TmdbCastMember {
    public synthetic <init>(int,java.lang.String,java.lang.String,java.lang.String,java.lang.Integer,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
