# Media3 / OkHttp ship their own consumer rules.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Saved settings store enum names as text (Kind, SourceType, Genre) and read them back with valueOf().
-keepclassmembers enum com.lumen.tv.** { *; }
