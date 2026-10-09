# JNI resolves these names and the progress method directly, including in minified apps.
-keep class com.commontongue.local.android.Native { *; }
-keep interface com.commontongue.local.android.NativeProgress { *; }
-keepclassmembers class * implements com.commontongue.local.android.NativeProgress {
    public void onStage(int);
}
