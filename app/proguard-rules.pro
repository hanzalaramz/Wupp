# Keep Go gomobile native bridge & methods accessed via reflection
-keep class com.wa.bridge.** { *; }
-dontwarn com.wa.bridge.**
-keep class go.** { *; }
-dontwarn go.**
-keep class whatsbridge.** { *; }
-dontwarn whatsbridge.**

# Keep Mozilla GeckoView engine & JNI bindings
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.**

# Keep Room database & entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.Query <methods>;
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
}

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wa.beastclient.**$$serializer { *; }
-keepclassmembers class com.wa.beastclient.** {
    *** Companion;
}
-keepclasseswithmembers class com.wa.beastclient.** {
    kotlinx.serialization.KSerializer serializer(...);
}
