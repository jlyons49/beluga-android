# BouncyCastle rules
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ZXing Core rules
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# Beluga Protocol Models & Crypto
-keep class com.beluga.e2ee.protocol.model.** { *; }
-keep class com.beluga.e2ee.crypto.** { *; }
