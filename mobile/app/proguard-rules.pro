-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.example.mobile.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.mobile.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.mobile.model.**$$serializer { *; }

-dontwarn org.slf4j.**
-dontwarn io.ktor.**

