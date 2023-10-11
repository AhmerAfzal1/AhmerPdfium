plugins {
    id("com.android.application") version "8.1.2" apply false
    id("com.android.library") version "8.1.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.10" apply false
    id("com.google.dagger.hilt.android") version "2.48.1" apply false
    id("com.google.devtools.ksp") version "1.9.10-1.0.13" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.6.0" apply false
    id("io.codearte.nexus-staging") version "0.30.0"
    id("org.jetbrains.dokka") version "1.8.20"
}

val mOSSRHUsername: String? = System.getenv("OSSRH_USERNAME")
val mOSSRHPassword: String? = System.getenv("OSSRH_PASSWORD")

nexusStaging {
    serverUrl = "https://s01.oss.sonatype.org/service/local/"
    username = mOSSRHUsername
    password = mOSSRHPassword
    packageGroup = "io.github.ahmerafzal1"
}

apply(plugin = "io.codearte.nexus-staging")
subprojects {
    apply(plugin = "org.jetbrains.dokka")
}