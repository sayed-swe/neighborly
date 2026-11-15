# kotlinx.serialization: keep generated serializers for @Serializable DTOs
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.neighborly.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.neighborly.app.**$$serializer { *; }

# Ktor / OkHttp optional dependencies
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
