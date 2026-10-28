pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}
rootProject.name = "NoirMusic"
include(":app")
// The PC app's engine: plain Kotlin/JVM, reusing the TV app's YouTube Music and Mood DJ code
include(":engine")
project(":engine").projectDir = file("desktop/engine")
