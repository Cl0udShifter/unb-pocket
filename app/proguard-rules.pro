# Regras R8 / ProGuard do UnB Pocket

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.unbpocket.**$$serializer { *; }
-keepclassmembers class com.unbpocket.** {
    *** Companion;
}
-keepclasseswithmembers class com.unbpocket.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities, DAOs e Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# WorkManager workers e BroadcastReceivers do Core
-keep class com.unbpocket.core.data.worker.** { *; }
-keep class com.unbpocket.core.data.notification.** { *; }

# PdfBox-Android e dependencias opcionais de JVM
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# OkHttp / Okio / Jsoup / Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.jsoup.**
