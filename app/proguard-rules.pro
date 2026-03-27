-keepattributes *Annotation*

# NovaCut models (Room entities + serialization)
-keep class com.novacut.editor.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.DatabaseConfiguration
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.novacut.editor.engine.db.Converters { *; }

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep,allowobfuscation,allowshrinking class dagger.hilt.android.internal.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class com.novacut.editor.engine.** { *; }
-keep class com.novacut.editor.ai.** { *; }
-dontwarn dagger.hilt.internal.**

# Compose — keep runtime + reflection needs
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keepclassmembers class * {
    @androidx.compose.ui.tooling.preview.Preview <methods>;
}
-dontwarn androidx.compose.**

# Compose Material 3
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.material3.**

# Navigation Compose
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# Lifecycle
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# Media3 (ExoPlayer, Transformer, Effect)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Kotlin serialization / reflection
-keepclassmembers class * extends java.lang.Enum {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ONNX Runtime (Whisper speech-to-text)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# MediaPipe (selfie segmentation)
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# WorkManager + HiltWorker (workers instantiated by class name via reflection)
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }
-keep class androidx.hilt.work.** { *; }
-dontwarn androidx.work.**

# Lottie (uses reflection for text delegates and layer names)
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# DataStore Preferences (serializes keys by property name)
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# OkHttp (cloud inpainting API)
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Suppress common warnings
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
