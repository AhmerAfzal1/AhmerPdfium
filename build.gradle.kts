plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.navigation.safe.args) apply false
    alias(libs.plugins.nexus.staging)
    alias(libs.plugins.dokka)
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