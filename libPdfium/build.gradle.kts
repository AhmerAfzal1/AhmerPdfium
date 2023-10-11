@file:Suppress("UnstableApiUsage")

import java.net.URI

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}

group = "io.github.ahmerafzal1"
version = "1.8.1"

val mKeyId: String? = System.getenv("SIGNING_KEY_ID")
val mPassword: String? = System.getenv("SIGNING_PASSWORD")
val mSecretKeyRingKey: String? = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
val mOSSRHUsername: String? = System.getenv("OSSRH_USERNAME")
val mOSSRHPassword: String? = System.getenv("OSSRH_PASSWORD")

extra["ossrhUsername"] = mOSSRHUsername
extra["ossrhPassword"] = mOSSRHPassword

android {
    namespace = "com.ahmer.pdfium"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    buildFeatures {
        //noinspection DataBindingWithoutKapt
        dataBinding = true
        viewBinding = true
    }

    defaultConfig {
        minSdk = 19

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
            version = cmake.version
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}

/**
 * Retrieves the [sourceSets][SourceSetContainer] extension.
 */
val Project.sourceSets: SourceSetContainer
    get() = (this as ExtensionAware).extensions.getByName("sourceSets") as SourceSetContainer

/**
 * Provides the existing [main][SourceSet] element.
 */
val SourceSetContainer.main: NamedDomainObjectProvider<SourceSet>
    get() = named<SourceSet>("main")

tasks.withType<GenerateModuleMetadata> {
    enabled = true
}

tasks.register<Jar>("releaseSourcesJar") {
    from(android.sourceSets["main"].java.srcDirs)
    archiveClassifier.set("sources")
}

tasks.register<Jar>("javadocJar") {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    archiveClassifier.value("javadoc")
    from(tasks.getByName("dokkaJavadoc"))
}

tasks.named<Jar>("releaseSourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

artifacts {
    archives(tasks["javadocJar"])
    archives(tasks["releaseSourcesJar"])
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                // The coordinates of the library, being set from variables that we'll set up in a moment
                artifact(tasks["javadocJar"])
                artifact(tasks["releaseSourcesJar"])
                artifact(tasks["bundleReleaseAar"])
                artifactId = "ahmer-pdfium"

                // Self-explanatory metadata for the most part
                pom {
                    name.set("AhmerPdfium")
                    description.set("Ahmer Pdfium Library for Android (API 19 binding)")
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

                    // Version control info, if you're using GitHub, follow the format as seen here
                    scm {
                        connection.set("scm:git:git://github.com/AhmerAfzal1/AhmerPdfium.git")
                        developerConnection.set("scm:git:ssh://github.com/AhmerAfzal1/AhmerPdfium.git")
                        url.set("https://github.com/AhmerAfzal1/AhmerPdfium/tree/master/Pdfium")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "mavenCentral"

                val releaseUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                val snapshotsUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                val releaseRepoUrl = URI.create(releaseUrl)
                val snapshotsRepoUrl = URI.create(snapshotsUrl)
                val isSnapshot = version.toString().endsWith("SNAPSHOT")
                url = if (isSnapshot) snapshotsRepoUrl else releaseRepoUrl

                if (mOSSRHUsername != null && mOSSRHPassword != null) {
                    credentials {
                        username = mOSSRHUsername
                        password = mOSSRHPassword
                    }
                }
            }
        }
    }

    configure<SigningExtension> {
        extra["signing.keyId"] = mKeyId
        extra["signing.password"] = mPassword
        extra["signing.secretKeyRingFile"] = mSecretKeyRingKey

        val pubExt = checkNotNull(extensions.findByType(PublishingExtension::class.java))
        val publication = pubExt.publications["release"]
        sign(publication)
    }
}