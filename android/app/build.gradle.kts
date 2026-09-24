plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.babeltech.babelkey"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.babeltech.babelkey"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Release signing — generate via: keytool -genkeypair -alias babelkey -keyalg RSA -keysize 2048 -validity 10000 -keystore babelkey.jks
        // CI should inject passwords via env KEYSTORE_PASSWORD / KEY_PASSWORD
        // For local debug builds without a keystore, signingConfig remains unset (uses debug keystore)
        create("release") {
            val keystoreFile = file(System.getenv("BABELKEY_KEYSTORE") ?: "babelkey.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("BABELKEY_STORE_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("BABELKEY_KEY_ALIAS") ?: "babelkey"
                keyPassword = System.getenv("BABELKEY_KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Only sign with release keystore if present; otherwise use debug signing for local builds
            val ks = file(System.getenv("BABELKEY_KEYSTORE") ?: "babelkey.jks")
            if (ks.exists()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Jetpack Compose (BOM)
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Jetpack Security — EncryptedSharedPreferences per spec
    implementation(libs.security.crypto)

    // Compose Google Fonts (Phase 2 — opt-in, HTTPS)
    implementation(libs.compose.ui.text.google.fonts)

    // Play Services Auth API Phone — SMS Retriever for OTP (Phase 2)
    implementation(libs.play.services.auth.api.phone)

    // ML Kit Smart Reply — on-device (Phase 3, kept but stubbed in Phase 1)
    // Keep as implementation so the class resolves, but feature is gated OFF by default
    implementation("com.google.mlkit:smart-reply:17.0.4")

    // Unit testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Instrumentation
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
