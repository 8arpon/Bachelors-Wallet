plugins {
    id("com.google.gms.google-services")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.2.0"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


    }

    buildTypes {
        release {
            isMinifyEnabled = false // HIGHLIGHT: এইটা false করে দিন
            isShrinkResources = false // HIGHLIGHT: এইটাও false করে দিন
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("io.coil-kt:coil-compose:2.6.0") // ছবির জন্য
    implementation("com.google.firebase:firebase-storage") // ছবি ক্লাউডে সেভ করার জন্য
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // HIGHLIGHT: Added Gson for Central Database
    implementation("com.google.code.gson:gson:2.10.1")

    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))

// Firebase Analytics (Optional but recommended)
    implementation("com.google.firebase:firebase-analytics")

// Firebase Authentication (লগইন এর জন্য)
    implementation("com.google.firebase:firebase-auth")

// Firebase Firestore (ক্লাউড ডাটাবেস এর জন্য)
    implementation("com.google.firebase:firebase-firestore")

}

base {
    archivesName.set("Bachelor's Wallet")
}