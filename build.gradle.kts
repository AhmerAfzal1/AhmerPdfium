plugins {
    id("com.android.application") version "8.1.0" apply false
    id("com.android.library") version "8.1.0" apply false
    id("org.jetbrains.kotlin.android") version "1.8.0" apply false
    id("org.jetbrains.kotlin.jvm") version "1.8.0" apply false
}/*

fun Project.configureBaseExtension() {
    extensions.findByType(BaseExtension::class)?.run {
        compileSdkVersion(34)
        buildToolsVersion = "33.0.1"
        ndkVersion = "25.2.9519653"

        defaultConfig {
            minSdk = 19
            targetSdk = 34
            versionCode = 10
            versionName = "1.7.0"

            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DANDROID_STL=c++_static",
                        "-DANDROID_PLATFORM=android-${minSdk.toString()}",
                        "-DANDROID_ARM_NEON=TRUE"
                    )
                    cppFlags += "-std=c++17 -frtti -fexceptions"
                }
            }

            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
        }

        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        extensions.findByType(ApplicationExtension::class)?.lint {
            checkReleaseBuilds = false
            checkAllWarnings = true
            warningsAsErrors = false
            abortOnError = false
            disable.addAll(setOf("TypographyFractions", "TypographyQuotes", "Typos"))
        }
    }
}

fun Project.configureJavaExtension() {
    extensions.findByType(JavaPluginExtension::class.java)?.run {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

subprojects {
    plugins.withId("com.android.application") {
        configureBaseExtension()
    }
    plugins.withId("com.android.library") {
        configureBaseExtension()
    }
    plugins.withId("org.gradle.java-library") {
        configureJavaExtension()
    }
}*/