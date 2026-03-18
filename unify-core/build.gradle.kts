plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "me.jordanfails"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.aikar.co/content/groups/aikar/") {
        name = "aikar"
    }
    maven("https://maven.enginehub.org/repo/") {
        name = "enginehub"
    }
}

dependencies {
    // Compile against 1.16.5 API (has modern Material names, runs on Java 8-16)
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("com.mojang:authlib:1.5.25") // Java 8 compatible version
    
    // Adventure API + MiniMessage support (bundled for non-Paper/legacy servers)
    implementation("net.kyori:adventure-api:4.14.0")
    implementation("net.kyori:adventure-text-minimessage:4.14.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.14.0")
    implementation("net.kyori:adventure-platform-bukkit:4.4.1")
    
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("com.github.cryptomorin:XSeries:10.0.0")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
    implementation("me.jordanfails:honey:1.0.0")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.5")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.0")
//    compileOnly("com.mojang:authlib:3.18.38")

}

// Default compilation for Java 8 (maximum compatibility)
kotlin {
    jvmToolchain(8)
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks {
    // Legacy shadow jar (Java 8 - for 1.8 to 1.16 servers)
    val shadowJarLegacy by registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        archiveBaseName.set("Unify")
        archiveClassifier.set("legacy")
        
        // Ensure Java 8 bytecode
        manifest {
            attributes["Multi-Release"] = false
        }
    }
    
    // Modern shadow jar (Java 17 - for 1.17+ servers)
    val shadowJarModern by registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        archiveBaseName.set("Unify")
        archiveClassifier.set("modern")
    }
    
    // Default shadowJar uses legacy for compatibility
    shadowJar {
        archiveBaseName.set("Unify")
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}
