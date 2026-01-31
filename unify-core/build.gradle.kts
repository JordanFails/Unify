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
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("com.mojang:authlib:3.13.56")
    implementation(kotlin("stdlib"))
    implementation("com.github.cryptomorin:XSeries:10.0.0")

    // include whichever version handlers you want shaded in
//    implementation(project(":unify-nms:unify-nms-v1_8_R3"))
//    implementation(project(":unify-nms:unify-nms-v1_9_R2"))
//    implementation(project(":unify-nms:unify-nms-v1_12_R1"))
//    implementation(project(":unify-nms:unify-nms-v1_16_R3"))
//    implementation(project(":unify-nms:unify-nms-v1_20_R4"))
//    implementation(project(":unify-nms:unify-nms-v1_21_R1"))
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
    shadowJar {
        archiveBaseName.set("Unify")
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}
