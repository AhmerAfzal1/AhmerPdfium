plugins {
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.20" apply false
    id("com.google.dagger.hilt.android") version "2.56.1" apply false
    id("com.google.devtools.ksp") version "2.1.20-2.0.0" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.8.9" apply false
    id("io.codearte.nexus-staging") version "0.30.0"
    id("org.jetbrains.dokka") version "2.0.0"
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