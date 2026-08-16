# Complication data sources and the Data Layer listener are instantiated by the system from the
# manifest, never referenced from code, so R8 cannot see that they are used.
-keep class com.salahwatch.wear.complications.** { *; }
-keep class com.salahwatch.wear.PrayerSyncListenerService { *; }
-keep class com.salahwatch.wear.TimeChangeReceiver { *; }

# WorkManager instantiates workers reflectively by class name.
-keep class * extends androidx.work.ListenableWorker { *; }

# kotlinx.serialization generates serializers referenced only through the plugin's intrinsics.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.salahwatch.** {
    *** Companion;
}
-keepclasseswithmembers class com.salahwatch.** {
    kotlinx.serialization.KSerializer serializer(...);
}
