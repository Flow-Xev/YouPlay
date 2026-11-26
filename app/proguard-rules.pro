# Keep InnerTubeX / kotlinx serialization models
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
