import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "com.ahmer.pdfviewer"
    compileSdk = 36
    buildToolsVersion = "36.1.0"
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile(name = "proguard-android-optimize.txt"), "proguard-rules.pro")
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
        disable.addAll(elements = setOf("TypographyFractions", "TypographyQuotes", "Typos"))
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=org.readium.r2.shared.InternalReadiumApi"
            )
        }
    }
}

dependencies {
    implementation(project(":libPdfium"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.play.services)
}

// =============== Publication Setup ===============
tasks.dokkaHtml.configure {
    outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    moduleName.set("AhmerPdfViewer")

    dokkaSourceSets {
        named(name = "main") {
            sourceLink {
                localDirectory.set(file(path = "src/main/java"))
                remoteUrl.set(uri(path = "https://github.com/AhmerAfzal1/AhmerPdfium/tree/main/libPdfViewer/src/main/java").toURL())
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
        artifactId = "ahmer-pdfviewer",
        version = "2.0.1"
    )

    pom {
        name.set("AhmerPDFViewer")
        description.set("Android view for displaying PDFs rendered with PdfiumAndroid")
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
            url.set("https://github.com/AhmerAfzal1/AhmerPdfium/tree/master/libPdfViewer/")
            connection.set("scm:git:git://github.com/AhmerAfzal1/AhmerPdfium.git")
            developerConnection.set("scm:git:ssh://git@github.com/AhmerAfzal1/AhmerPdfium.git")
        }
    }
}