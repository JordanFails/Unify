plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "me.jordanfails"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.codemc.io/repository/nms/") {
        name = "codemc-nms-repo"
    }
}

dependencies {
    compileOnly(project(":unify-core"))
    compileOnly("org.spigotmc:spigot:1.16.5-R0.1-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
