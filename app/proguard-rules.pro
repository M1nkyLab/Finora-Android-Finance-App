# ─────────────────────────────────────────────────────────────
#  ProGuard / R8 rules — Finora (Dime) Finance App
# ─────────────────────────────────────────────────────────────

# ── Kotlin Serialization ────────────────────────────────────
# Keep @Serializable data classes used for Supabase decoding
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `@Serializable` annotated data classes
-keep,includedescriptorclasses class com.dime.app.data.**$$serializer { *; }
-keepclassmembers class com.dime.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.dime.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Supabase / Ktor ─────────────────────────────────────────
-dontwarn io.github.jan.supabase.**
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# ── Hilt / Dagger ───────────────────────────────────────────
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Compose ─────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Google Generative AI (Gemini) ───────────────────────────
-dontwarn com.google.ai.client.generativeai.**
-keep class com.google.ai.client.generativeai.** { *; }

# ── General ─────────────────────────────────────────────────
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
