import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
}

// Environment variables for signing and publishing
val keyId: String? = System.getenv("SIGNING_KEY_ID")
val password: String? = System.getenv("SIGNING_PASSWORD")
val secretKey: String? = System.getenv("SIGNING_SECRET_KEY")
val centralUsername: String? = System.getenv("CENTRAL_USERNAME")
val centralPassword: String? = System.getenv("CENTRAL_PASSWORD")

extra["centralUsername"] = centralUsername
extra["centralPassword"] = centralPassword

android {
    namespace = "com.ahmer.pdfium"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "29.0.13113456 rc1"

    buildFeatures {
        //noinspection DataBindingWithoutKapt
        dataBinding = true
        viewBinding = true
    }

    defaultConfig {
        minSdk = 24

        externalNativeBuild {
            cmake {
                arguments.addAll(
                    listOf(
                        "-DANDROID_STL=c++_static",
                        "-DANDROID_PLATFORM=android-${minSdk}",
                        "-DANDROID_ARM_NEON=TRUE"
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }

    externalNativeBuild {
        cmake {
            version = "3.31.6"
            path("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JvmTarget.JVM_17.target
    }

    lint {
        checkAllWarnings = true
        warningsAsErrors = false
        abortOnError = false
        disable.addAll(setOf("TypographyFractions", "TypographyQuotes", "Typos"))
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.play.services)
}

// =============== Publication Setup ===============
val javadocJar by tasks.register<Jar>("javadocJar") {
    dependsOn(tasks.named("dokkaJavadoc"))
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaJavadoc"))
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(javadocJar)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "io.github.ahmerafzal1"
            artifactId = "ahmer-pdfium"
            version = "1.8.2"
            afterEvaluate {
                from(components["release"])
            }

            // POM configuration
            pom {
                name.set("AhmerPdfium")
                description.set("Ahmer Pdfium Library for Android (API 24 binding)")
                url.set("https://github.com/AhmerAfzal1/AhmerPdfium")
                packaging = "aar"

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("ahmerafzal1")
                        name.set("Ahmer Afzal")
                        email.set("ahmerafzal@yahoo.com")
                        roles.set(listOf("owner", "developer"))
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/AhmerAfzal1/AhmerPdfium.git")
                    developerConnection.set("scm:git:ssh://github.com/AhmerAfzal1/AhmerPdfium.git")
                    url.set("https://github.com/AhmerAfzal1/AhmerPdfium/tree/master/libPdfium/")
                }
            }
        }
    }

    repositories {
        maven {
            name = "central"
            url = URI.create("https://central.sonatype.com/")

            credentials {
                username = centralUsername ?: ""
                password = centralPassword ?: ""
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["release"])
}