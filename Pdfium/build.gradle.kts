plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlin.android")
}

val envKeyId: String? = System.getenv("SIGNING_KEY_ID")
val envPassword: String? = System.getenv("SIGNING_PASSWORD")
val envSecretKey: String? = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
val envOssrhUsername: String? = System.getenv("OSSRH_USERNAME")
val envOssrhPassword: String? = System.getenv("OSSRH_PASSWORD")

android {
    compileSdk = 34
    buildToolsVersion = "33.0.1"
    ndkVersion = "25.2.9519653"
    namespace = "com.ahmer.pdfium"

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        minSdk = 19
        targetSdk = 34

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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("androidx.core:core-ktx:1.10.1")
}

val androidExtension = extensions.findByType<com.android.build.gradle.LibraryExtension>()

val jarSources by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    androidExtension?.sourceSets?.get("main")?.java?.let { from(it.srcDirs) }
}

val jarJavadoc by tasks.creating(Jar::class) {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // The coordinates of the library, being set from variables that we'll set up in a moment
            artifact(jarSources)
            artifact(jarJavadoc)
            groupId = "io.github.ahmerafzal1"
            artifactId = "ahmer-pdfium"
            version = android.defaultConfig.versionName

            // Self-explanatory metadata for the most part
            pom {
                name.set("AhmerPdfium")
                description.set("Ahmer Pdfium Library for Android (API 19 binding)")
                url.set("https://github.com/AhmerAfzal1/AhmerPdfium")
                //packaging.set("aar")
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
            name = "maven"

            val releaseRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            // You only need this if you want to publish snapshots, otherwise just set the URL
            // to the release repo directly
            if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releaseRepoUrl

            credentials {
                username = envOssrhUsername
                password = envOssrhPassword
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}