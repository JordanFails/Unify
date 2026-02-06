plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "8.3.0"
    id("io.papermc.paperweight.userdev") version "1.7.1"
}

group = "me.jordanfails"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    compileOnly(project(":unify-core"))
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    assemble {
        dependsOn(reobfJar)
    }
}