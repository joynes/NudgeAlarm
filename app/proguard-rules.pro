# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

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

# SnakeYAML uses java.beans.* which is not available on Android
-dontwarn java.beans.**

# Keep SnakeYAML classes used for config parsing
-keep class org.yaml.snakeyaml.** { *; }

# Keep Room entity and DAO classes
-keep class org.nudgealarm.app.database.** { *; }

# Keep Gson serialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep stack traces readable
-keepattributes SourceFile,LineNumberTable