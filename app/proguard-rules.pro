# SafeVault — R8 / ProGuard rules
# Full-mode R8 is enabled implicitly by AGP 8 (android.enableR8.fullMode default).
#
# The Room / Hilt / biometric entries below are broad wildcard keeps, not
# minimal ones. All three libraries ship consumer rules that should make them
# unnecessary, so they cost shrinking for insurance against a full-mode R8
# surprise in a security-critical release build. Narrowing them requires
# smoke-testing an actual release APK on a device, not just reading the rules.

# --- General attributes ---------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-keepattributes InnerClasses,EnclosingMethod

# --- Room -----------------------------------------------------------------
# Conservative: Room's generated code touches entity/DAO members reflectively.
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger --------------------------------------------------------
# Conservative: Hilt ships consumer rules; this is belt-and-braces.
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
