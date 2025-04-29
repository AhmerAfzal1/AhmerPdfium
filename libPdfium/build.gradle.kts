import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
}

group = "io.github.ahmerafzal1"
version = "1.8.2"

// Environment variables for signing and publishing
val mKeyId: String? = System.getenv("SIGNING_KEY_ID")
val mPassword: String? = System.getenv("SIGNING_PASSWORD")
val mSecretKey: String? = System.getenv("SIGNING_SECRET_KEY")
val mOSSRHUsername: String? = System.getenv("OSSRH_USERNAME")
val mOSSRHPassword: String? = System.getenv("OSSRH_PASSWORD")

extra["ossrhUsername"] = mOSSRHUsername
extra["ossrhPassword"] = mOSSRHPassword

android {
    namespace = "com.ahmer.pdfium"
    compileSdk = 36
    ndkVersion = "29.0.13113456"

    buildFeatures {
        //noinspection DataBindingWithoutKapt
        dataBinding = true
        viewBinding = true
    }

    defaultConfig {
        minSdk = 21

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

val sourcesJar by tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(android.sourceSets["main"].java.srcDirs)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                // Artifact configuration
                artifactId = "ahmer-pdfium"
                artifact(layout.buildDirectory.file("outputs/aar/${project.name}-release.aar"))
                artifact(sourcesJar)
                artifact(javadocJar)

                // POM configuration
                pom {
                    name.set("AhmerPdfium")
                    description.set("Ahmer Pdfium Library for Android (API 21 binding)")
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
                        url.set("https://github.com/AhmerAfzal1/AhmerPdfium/tree/master/Pdfium")
                    }
                }

                // Dependency info
                pom.withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    configurations["implementation"].allDependencies.forEach {
                        if (it.name != "unspecified") {
                            val dependencyNode = dependenciesNode.appendNode("dependency")
                            dependencyNode.appendNode("groupId", it.group)
                            dependencyNode.appendNode("artifactId", it.name)
                            dependencyNode.appendNode("version", it.version)
                            dependencyNode.appendNode("scope", "implementation")
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "MavenCentral"
                url = URI.create(
                    if (version.toString().endsWith("SNAPSHOT"))
                        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    else
                        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                )

                credentials {
                    username = mOSSRHUsername ?: ""
                    password = mOSSRHPassword ?: ""
                }
            }
        }
    }

    signing {
        useInMemoryPgpKeys(mKeyId, mSecretKey, mPassword)
        sign(publishing.publications["release"])
    }
}