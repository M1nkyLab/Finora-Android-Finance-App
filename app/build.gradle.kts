import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kapt {
    correctErrorTypes = true
}

android {
    namespace = "com.dime.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dime.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Read secrets from local.properties
        val localProps = rootProject.file("local.properties")
        val props = if (localProps.exists()) Properties().also { it.load(localProps.inputStream()) } else Properties()
        val geminiKey = props.getProperty("GEMINI_API_KEY", "")
        val supabaseUrl = props.getProperty("SUPABASE_URL", "")
        val supabaseAnonKey = props.getProperty("SUPABASE_ANON_KEY", "")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose (API 34 compatible BOM)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Glance App Widget
    implementation("androidx.glance:glance-appwidget:1.1.0-alpha01")
    implementation("androidx.glance:glance-material3:1.1.0-alpha01")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Icons
    implementation("androidx.compose.material:material-icons-extended")

    // Google Generative AI (Gemini)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Supabase
    implementation(platform("io.github.jan-tennermann:supabase-kt-bom:2.6.1"))
    implementation("io.github.jan-tennermann:postgrest-kt")
    implementation("io.github.jan-tennermann:gotrue-kt")
    implementation("io.github.jan-tennermann:realtime-kt")

    // Ktor engine (required by Supabase client on Android)
    implementation("io.ktor:ktor-client-android:2.3.12")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}