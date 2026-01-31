plugins {
    // declare plugin ids using full form (not kotlin("..."))
    id("org.jetbrains.kotlin.jvm") version "1.9.22" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

subprojects {
    // apply kotlin plugin properly
    plugins.apply("org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    // use typed access to the Kotlin extension
    the<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>().apply {
        jvmToolchain(21)
    }
}