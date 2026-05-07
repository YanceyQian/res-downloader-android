# Add project specific ProGuard rules here.
-keepattributes *Annotation*

# Keep Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }

# Keep data classes
-keep class com.resdownloader.data.model.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
