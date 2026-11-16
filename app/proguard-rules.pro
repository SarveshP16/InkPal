# jsoup's optional regex engine isn't on the classpath.
-dontwarn com.google.re2j.**

# Called from the reader's JavaScript by name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Saved data is (de)serialized by name; keep the models intact.
-keep class com.inkpal.app.data.** { *; }
