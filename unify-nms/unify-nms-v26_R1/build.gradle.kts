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
    maven("https://repo.codemc.io/repository/maven-public/") {
        name = "codemc"
    }
}

dependencies {
    compileOnly(project(":unify-core"))
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.15.5")
    paperweight.paperDevBundle("26.2.build.48-alpha")
}

kotlin {
    jvmToolchain(26)
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

tasks {
    assemble {
        dependsOn(reobfJar)
    }
}
