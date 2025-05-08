import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
}

val keyId: String? = System.getenv("SIGNING_KEY_ID")
val password: String? = System.getenv("SIGNING_PASSWORD")
val secretKey: String? = System.getenv("SIGNING_SECRET_KEY")
val centralUsername: String? = System.getenv("CENTRAL_USERNAME")
val centralPassword: String? = System.getenv("CENTRAL_PASSWORD")

extra["centralUsername"] = centralUsername
extra["centralPassword"] = centralPassword

android {
    namespace = "com.ahmer.pdfviewer"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "29.0.13113456 rc1"

    defaultConfig {
        minSdk = 24
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

    lint {
        checkAllWarnings = true
        warningsAsErrors = false
        abortOnError = false
        disable.addAll(setOf("TypographyFractions", "TypographyQuotes", "Typos"))
    }

    kotlinOptions {
        jvmTarget = JvmTarget.JVM_17.target
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn", "-opt-in=org.readium.r2.shared.InternalReadiumApi"
        )
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(project(":libPdfium"))
    implementation(libs.androidx.core.ktx)
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
            artifactId = "ahmer-pdfviewer"
            version = "1.7.3"
            afterEvaluate {
                from(components["release"])
            }

            // POM configuration
            pom {
                name.set("AhmerPDFViewer")
                description.set("Android view for displaying PDFs rendered with PdfiumAndroid")
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
                    url.set("https://github.com/AhmerAfzal1/AhmerPdfium/tree/master/libPdfViewer")
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

    signing {
        useGpgCmd()
        sign(publishing.publications["release"])
    }
}