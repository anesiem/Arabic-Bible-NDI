# v1.9: R8 is enabled. Keep rules for JNI, the foreground service, and JSON models.
# Verify the release build on device before shipping.

# NDI native bridge — native methods must keep their exact names for JNI lookup.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.arabicchristianmedia.server.NdiNativeSender { *; }

# Foreground service for background NDI broadcast (referenced from manifest).
-keep class com.arabicchristianmedia.server.NdiService { *; }

# Template/style models: serialized to JSON/export payloads; keep stable.
-keep class com.arabicchristianmedia.model.** { *; }

# Moshi/Retrofit are currently unused dependencies. If re-enabled, keep the
# generated adapters:
# -keep class **_JsonAdapter { *; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
