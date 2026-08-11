# Keep the attributes used by Android's generated components and the remaining
# runtime annotations. The default optimize file carries the same baseline;
# this explicit list documents the attributes this app relies on without
# retaining every annotation-shaped attribute.
-keepattributes AnnotationDefault,EnclosingMethod,InnerClasses,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations,Signature

# Room's generated ProjectDatabase_Impl is constructed by RoomDatabase's
# generated-implementation lookup. The equivalent consumer rule is also
# shipped by room-runtime; keep this app-side declaration close to the usage
# because it is a real reflection edge, not a package-wide Room keep.
-keep class * extends androidx.room3.RoomDatabase { void <init>(); }

# The optional integrations below are discovered by Class.forName. Keep only
# the named probe classes and allow R8 to optimize their members. Optional
# libraries remain optional: a missing class simply keeps the existing feature
# gate false. Directly referenced methods (FFmpeg and DeepFilterNet) remain
# visible to R8 through normal call-graph analysis.
-keep,allowoptimization class androidx.camera.video.VideoCapture
-keep,allowoptimization class org.contentauth.c2pa.Builder
-keep,allowoptimization class org.proofmode.simplec2pa.Manifest
-keep,allowoptimization class com.arthenica.ffmpegkit.FFmpegKit
-keep,allowoptimization class com.rikorose.deepfilternet.NativeDeepFilterNet
-keep,allowoptimization class com.kaleyra.noise_filter.DeepFilterNet
-keep,allowoptimization class io.github.thibaultbee.streampack.streamers.SingleStreamer
-keep,allowoptimization class com.wmspanel.libstream.Streamer
-keep,allowoptimization class com.haivision.srtkit.Srt
-keep,allowoptimization class com.google.oboe.MultiChannelResampler
-keep,allowoptimization class app.rive.runtime.kotlin.RiveAnimationView

# Native methods are covered by proguard-android-optimize.txt. Hilt, Room,
# WorkManager, Media3, Coil, DataStore, OkHttp, ONNX Runtime, MediaPipe, and
# Lottie likewise contribute their own consumer rules; do not duplicate those
# rules here with package-wide keeps.

# Suppress compile-only/provider warnings that are independent of the app's
# reflective entry points. These directives do not retain code.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**

# Strip verbose/debug logging from release builds. Warning/error logs are kept
# deliberately — they carry the failure diagnostics referenced in CLAUDE.md.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
