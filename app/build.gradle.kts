plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.legendpolyfoams.lpplpms"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.legendpolyfoams.lpplpms"
        minSdk = 26
        targetSdk = 35
        versionCode = 109
        versionName = "1.3.5-native"
        buildConfigField("String", "BASE_API_URL", "\"https://script.google.com/macros/s/AKfycbxG9BCPDBsQcrViIbJs7JTfPEvT5a4O9f8NiFwRu0Ij5JKx6PrhqQninPDZz4K2E_QO/exec\"")
    }
    val releaseKeystorePath = System.getenv("LPPL_KEYSTORE_PATH")
    if (!releaseKeystorePath.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("LPPL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LPPL_KEY_ALIAS")
                keyPassword = System.getenv("LPPL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!releaseKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
