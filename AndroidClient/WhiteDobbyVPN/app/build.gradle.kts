plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.whitedobby"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.whitedobby"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Firebase BoM (Bill of Materials)
    implementation(libs.firebase.bom)
    // Firebase Firestore
    implementation(libs.firebase.firestore.ktx)
    // Firebase Cloud Storage
    implementation(libs.firebase.storage.ktx)

    // Firebase Cloud Messaging
    implementation ("com.google.firebase:firebase-messaging-ktx")
    // Navigation Compose
    implementation ("androidx.navigation:navigation-compose:2.8.4")
    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-analytics")

    //implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Credential Manager
    implementation(libs.androidx.credentials)
    // Библиотека для интеграции Google Identity через Credential Manager
    implementation(libs.androidx.credentials.play.services.auth)
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(libs.coil.compose)
    // Зависимости для Google Sign-In
    implementation(libs.play.services.auth)
    // Jetpack Compose зависимости
    implementation (libs.ui)
    implementation (libs.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.firebase.auth.ktx)
    //implementation(libs.androidx.work.testing)
    implementation(libs.core.ktx)
    implementation(libs.junit.junit)
    implementation(libs.googleid)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.work.testing)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}