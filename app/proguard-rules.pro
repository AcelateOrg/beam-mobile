# gomobile bindings: Go calls back into these classes by name through JNI.
-keep class go.** { *; }
-keep class mobile.** { *; }
-keepclassmembers class * implements mobile.SocketProtector { *; }
-keepclassmembers class * implements mobile.ProcessResolver { *; }

# ZXing and CameraX are used through reflection-free APIs, but keep ZXing's readers whole to be safe.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
