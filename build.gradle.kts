plugins {
    id("com.android.application") version "8.1.0" apply false
    id("com.android.library") version "8.1.0" apply false
    id("com.google.dagger.hilt.android") version "2.47" apply false
    id("com.google.devtools.ksp") version "1.9.0-1.0.12" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    id("org.jetbrains.dokka") version "1.8.20"
    id("io.codearte.nexus-staging") version "0.30.0"

}
apply(plugin = "io.codearte.nexus-staging")