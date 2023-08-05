@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ahmer.pdfium"
    compileSdk = 33

    buildFeatures {
        dataBinding = true
        viewBinding = true
    }

    defaultConfig {
        minSdk = 19
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static", "-DANDROID_PLATFORM=android-${minSdk.toString()}",
                    "-DANDROID_ARM_NEON=TRUE"
                )
                cppFlags += "-std=c++17 -frtti -fexceptions"
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkAllWarnings = true
        warningsAsErrors = false
        abortOnError = false
        disable.addAll(setOf("TypographyFractions", "TypographyQuotes", "Typos"))
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
}