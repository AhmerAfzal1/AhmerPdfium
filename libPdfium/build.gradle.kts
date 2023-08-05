import com.android.build.gradle.BaseExtension
import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

val mKeyId: String? = System.getenv("SIGNING_KEY_ID")
val mPassword: String? = System.getenv("SIGNING_PASSWORD")
val mSecretKeyRingKey: String? = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
val mOSSrhUsername: String? = System.getenv("OSSRH_USERNAME")
val mOSSrhPassword: String? = System.getenv("OSSRH_PASSWORD")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

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
                version = cmake.version.toString()
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