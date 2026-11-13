# Semua kelas (termasuk androidx/kotlin/firebase milik modul ini) dipindah ke paket "svx"
# supaya tidak bentrok dengan versi androidx/kotlin yang sudah ada di aplikasi utama.
-repackageclasses 'svx'
-allowaccessmodification
-dontwarn **
-keep class com.svara.ext.** { *; }
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.firebase.provider.FirebaseInitProvider { *; }
-keep class com.google.firebase.components.ComponentDiscoveryService { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrarProcessor { *; }
