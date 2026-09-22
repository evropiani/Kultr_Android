# kotlinx.serialization: keep generated serializers of Kultr's own models.
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers @kotlinx.serialization.Serializable class app.kultr.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class app.kultr.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
