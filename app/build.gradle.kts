plugins {
    alias(libs.plugins.android.application)
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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // ML Kit Smart Reply — on-device context-aware quick replies
    implementation("com.google.mlkit:smart-reply:17.0.4")
    // Unit testing
    testImplementation("junit:junit:4.13.2")
    // org.json is Android-bundled at runtime but needs an explicit dep for JVM unit tests
    testImplementation("org.json:json:20231013")
}