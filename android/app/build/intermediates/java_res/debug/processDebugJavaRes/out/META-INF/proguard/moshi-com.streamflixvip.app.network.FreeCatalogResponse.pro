-if class com.streamflixvip.app.network.FreeCatalogResponse
-keepnames class com.streamflixvip.app.network.FreeCatalogResponse
-if class com.streamflixvip.app.network.FreeCatalogResponse
-keep class com.streamflixvip.app.network.FreeCatalogResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.streamflixvip.app.network.FreeCatalogResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.streamflixvip.app.network.FreeCatalogResponse
-keepclassmembers class com.streamflixvip.app.network.FreeCatalogResponse {
    public synthetic <init>(java.util.List,int,java.lang.String,java.lang.Boolean,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
