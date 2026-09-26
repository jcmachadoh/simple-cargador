# Simple Cargador - Reglas R8/ProGuard

# --- Kotlinx Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class cu.jcmachadoh.simplecargador.**$$serializer { *; }
-keepclassmembers class cu.jcmachadoh.simplecargador.** { *** Companion; }
-keepclasseswithmembers class cu.jcmachadoh.simplecargador.** { kotlinx.serialization.KSerializer serializer(...); }

# --- Retrofit ---
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# --- OkHttp ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Room (el compilador genera implementaciones referenciadas por nombre) ---
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- Tink / security-crypto: anotaciones opcionales en tiempo de compilación ---
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# --- Widget (RF-09) ---
# Mantiene íntegras las clases del widget. El provider se mantiene por nombre
# (componente del manifest), pero AGP no reescribe sus referencias internas:
# si R8 elimina/ofusca WidgetUpdater, onUpdate lanza NoClassDefFoundError y el
# launcher muestra "No se pudo cargar el widget".
-keep class cu.jcmachadoh.simplecargador.widget.** { *; }
