# SafeVault — R8 / ProGuard rules
# Full-mode R8 is enabled implicitly by AGP 8 (android.enableR8.fullMode default).
# Keep only what reflection-based libraries actually need.

# --- General attributes ---------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-keepattributes InnerClasses,EnclosingMethod

# --- Room -----------------------------------------------------------------
# Room's generated code references entity/DAO members reflectively at times.
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger --------------------------------------------------------
# Hilt ships its own consumer rules, but keep generated components defensively.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# --- BiometricPrompt -------------------------------------------------------
-keep class androidx.biometric.** { *; }

# --- Compose ---------------------------------------------------------------
# The Compose compiler + R8 ship the required rules; nothing extra needed here.

# --- Domain models kept for clarity of stack traces ------------------------
-keepnames class com.safevault.app.domain.model.** { *; }

# --- Strip verbose logging in release builds -------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
