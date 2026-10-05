# MSAL references Surface Duo display-mask classes, which are left out of the build (see build.gradle.kts).
-dontwarn com.microsoft.device.display.**
# Optional and compile-time-only dependencies of MSAL's common library (OpenTelemetry, Nimbus JOSE,
# SpotBugs annotations) that are not on the classpath and not used at runtime.
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$*
-dontwarn com.google.crypto.tink.subtle.**
-dontwarn edu.umd.cs.findbugs.annotations.**
