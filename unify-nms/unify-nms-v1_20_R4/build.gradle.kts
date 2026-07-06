plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.4.3"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
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
    paperweight.paperDevBundle("1.20.6-R0.1-SNAPSHOT")
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