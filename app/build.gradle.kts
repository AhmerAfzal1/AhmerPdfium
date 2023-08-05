plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ahmer.afzal.pdfviewer"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.ahmer.afzal.pdfviewer"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(mapOf("path" to ":libPdfium")))
    implementation(project(mapOf("path" to ":libPdfViewer")))
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}