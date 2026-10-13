# Preserve Line Numbers and Annotations for Debugging
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# WorkManager & Room Keep Rules (Fixes "Failed to create an instance of androidx.work.impl.WorkDatabase")
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.impl.** { *; }
-keep class androidx.room.** { *; }
-keep class androidx.startup.** { *; }

# Google Play Services & AdMob Keep Rules
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# AndroidX Core Splashscreen Keep Rules
-keep class androidx.core.splashscreen.** { *; }

# CameraX Keep Rules
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }

# Coil Image Loader Keep Rules
-keep class coil.** { *; }
-keep interface coil.** { *; }

# CanHub Image Cropper Keep Rules
-keep class com.canhub.cropper.** { *; }
-keep interface com.canhub.cropper.** { *; }

# Jetpack Compose & Lifecycle Keep Rules
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }

# Keep App Components and FileProvider
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends androidx.core.content.FileProvider
