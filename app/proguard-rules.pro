# Conservative ProGuard rules. Tighten after first release-build test passes.

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class io.github.mayusi.isitcompatible.**$$serializer { *; }
-keepclassmembers class io.github.mayusi.isitcompatible.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.mayusi.isitcompatible.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.lifecycle.ViewModel
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.lifecycle.HiltViewModel class *

# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.* class * { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
