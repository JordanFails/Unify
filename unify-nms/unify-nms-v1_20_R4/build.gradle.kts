plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.4.3"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "me.jordanfails"
version = providers.gradleProperty("unifyVersion").get()

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

// Unify ships these Mojang-mapped (the modern bundle sets the mappings manifest), so the plain jar is the
// artifact; reobfuscating is wasted work on 1.20/1.21 and impossible on 26.x, which has no obfuscation.
paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION