import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.signing)
}

android {
    namespace = "com.ahmer.pdfium"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "29.0.13113456 rc1"

    defaultConfig {
        minSdk = 24

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments.addAll(
                    elements = listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DANDROID_PLATFORM=android-${minSdk}",
                        "-DANDROID_ARM_NEON=TRUE",
                    )
                )

                cppFlags("-std=c++17", "-frtti", "-fexceptions")
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile(name = "proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            version = "4.1.1"
            path(path = "src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    lint {
        checkAllWarnings = true
        warningsAsErrors = false
        abortOnError = false
        disable.addAll(elements = setOf("TypographyFractions", "TypographyQuotes", "Typos"))
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.play.services)
}

// =============== Publication Setup ===============
tasks.dokkaHtml.configure {
    outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    moduleName.set("AhmerPdfium")

    dokkaSourceSets {
        named(name = "main") {
            sourceLink {
                localDirectory.set(file(path = "src/main/java"))
                remoteUrl.set(uri(path = "https://github.com/AhmerAfzal1/AhmerPdfium/tree/main/libPdfium/src/main/java").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }
}

mavenPublishing {
    configure(
        platform = AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true,
        )
    )
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.ahmerafzal1",
        artifactId = "ahmer-pdfium",
        version = "1.9.2"
    )

    pom {
        name.set("AhmerPdfium")
        description.set("Ahmer Pdfium Library for Android (API 24 binding)")
        inceptionYear.set("2023")
        url.set("https://github.com/AhmerAfzal1/AhmerPdfium/")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("ahmerafzal1")
                name.set("Ahmer Afzal")
                email.set("ahmerafzal@yahoo.com")
                url.set("https://github.com/AhmerAfzal1/")
                roles.set(listOf("owner", "developer"))
            }
        }

        scm {
            url.set("https://github.com/AhmerAfzal1/AhmerPdfium/tree/master/libPdfium/")
            connection.set("scm:git:git://github.com/AhmerAfzal1/AhmerPdfium.git")
            developerConnection.set("scm:git:ssh://git@github.com/AhmerAfzal1/AhmerPdfium.git")
        }
    }
}