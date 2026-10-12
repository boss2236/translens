# ViewModels are created reflectively by ViewModelProvider (AndroidViewModel needs the
# (Application) constructor).
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep stack traces readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit finds its components (OCR, translate, language ID) reflectively; R8 full mode
# otherwise merges/renames them and the clients fail to initialise.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.odml.** { *; }
