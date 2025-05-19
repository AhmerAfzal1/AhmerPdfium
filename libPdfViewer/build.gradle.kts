import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost
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
}

dependencies {
    implementation(project(":libPdfium"))
    implementation(libs.androidx.core.ktx)
}

// =============== Publication Setup ===============
tasks.dokkaHtml.configure {
    outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    moduleName.set("AhmerPdfViewer")

    dokkaSourceSets {
        named("main") {
            sourceLink {
                localDirectory.set(file("src/main/java"))
                remoteUrl.set(uri("https://github.com/AhmerAfzal1/AhmerPdfium/tree/main/libPdfViewer/src/main/java").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }
}

mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true,
        )
    )
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId = "io.github.ahmerafzal1",
        artifactId = "ahmer-pdfviewer",
        version = "1.7.4"
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