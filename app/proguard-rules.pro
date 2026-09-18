# Keep gomobile/libv2ray JNI bindings.
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.conscrypt.**
