-keep class com.mecheri636.offline.data.db.** { *; }
-keep class com.mecheri636.offline.qr.** { *; }


# 636mecheri Release R8 Keep Rules
-keep class com.mecheri636.offline.data.db.** { *; }
-keep class com.mecheri636.offline.qr.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class androidx.camera.** { *; }
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

# Fix Release R8 crash: CompositionLocal LocalLifecycleOwner not present (AndroidX b/336842920)
-keepattributes *Annotation*
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }
-keep class androidx.lifecycle.compose.LocalLifecycleOwnerKt { *; }
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { public <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { public <init>(...); }
