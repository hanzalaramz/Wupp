# Keep Rust JNI bridge & native VoIP media pipeline entrypoints
-keep class com.wa.bridge.whatsbridge.** { *; }
-dontwarn com.wa.bridge.**
-keepclasseswithmembers class com.wa.beastclient.core.calling.NativeVoipAudioPipeline { *; }
-keepclasseswithmembers class com.wa.beastclient.core.calling.NativeVoipVideoPipeline { *; }

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
