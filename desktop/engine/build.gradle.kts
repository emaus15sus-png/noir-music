import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

// Shares the TV app's plain-Kotlin files instead of copying them
val shared = listOf("Yt.kt", "Song.kt", "Mood.kt").map { "com/noir/music/$it" }
sourceSets.main {
    kotlin.srcDir("src")
    kotlin.srcDir("../../app/src/main/java")
    kotlin.include(shared + "com/noir/music/engine/**")
}

dependencies {
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303") // built into Android, not into the JVM
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

// One runnable jar (engine.jar) that the desktop app ships and starts
tasks.register<Jar>("engineJar") {
    archiveFileName.set("engine.jar")
    manifest { attributes["Main-Class"] = "com.noir.music.engine.EngineKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    }
}
