# Add project specific ProGuard rules here.
-keepattributes *Annotation*

# Keep Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }

# Keep data classes
-keep class com.resdownloader.data.model.** { *; }

# Keep Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class dagger.internal.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.internal.Binding
-keep class * extends dagger.internal.ModuleAdapter
-keep class * extends dagger.internal.StaticInjection

# Keep Hilt generated classes
-keep class **Hilt_** { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# Keep Android Entry Points
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
