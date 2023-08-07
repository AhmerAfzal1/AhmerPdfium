plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}

group = "io.github.ahmerafzal1"
version = "1.7.1"

val mKeyId: String? = System.getenv("SIGNING_KEY_ID")
val mPassword: String? = System.getenv("SIGNING_PASSWORD")
val mSecretKeyRingKey: String? = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
val mOSSRHUsername: String? = System.getenv("OSSRH_USERNAME")
val mOSSRHPassword: String? = System.getenv("OSSRH_PASSWORD")

android {
    namespace = "com.ahmer.pdfviewer"
    compileSdk = 34
    ndkVersion = "25.2.9519653"

    defaultConfig {
        minSdk = 21
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
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn", "-opt-in=org.readium.r2.shared.InternalReadiumApi"
        )
    }
}

dependencies {
    implementation(project(mapOf("path" to ":libPdfium")))
    implementation("androidx.core:core-ktx:1.10.1")
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
    enabled = false
}

val javadocJar by tasks.registering(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    archiveClassifier.value("javadoc")
    from(tasks.getByName("dokkaJavadoc"))
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.value("sources")
    from(android.sourceSets.getByName("main").java.srcDirs)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // The coordinates of the library, being set from variables that we'll set up in a moment
            artifact(sourcesJar)
            artifact(javadocJar)
            //artifact("$buildDir/outputs/aar/PdfViewer-release.aar")
            artifactId = "ahmer-pdfviewer"

            afterEvaluate {
                from(components["release"])
            }

            // Self-explanatory metadata for the most part
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
                    url.set("https://github.com/AhmerAfzal1/AhmerPdfium/tree/master/PdfViewer")
                }
            }
        }
    }
    repositories {
        maven {
            name = "maven"

            val releaseRepoUrl =
                uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl =
                uri("https://oss.sonatype.org/content/repositories/snapshots/")
            // You only need this if you want to publish snapshots, otherwise just set the URL
            // to the release repo directly
            setUrl(provider {
                if (version.toString().endsWith("SNAPSHOT")
                ) snapshotsRepoUrl else releaseRepoUrl
            })

            if (mOSSRHUsername != null && mOSSRHPassword != null) {
                credentials {
                    username = mOSSRHUsername
                    password = mOSSRHPassword
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

publishing.publications.withType<MavenPublication>().all {
    signing.sign(this)
}