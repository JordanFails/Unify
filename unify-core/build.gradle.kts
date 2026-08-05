plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.4.3"
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
    maven("https://repo.codemc.io/repository/maven-public/") {
        name = "codemc"
    }
    maven("https://repo.codemc.io/repository/maven-releases/") {
        name = "codemc-releases"
    }
    maven("https://repo.aikar.co/content/groups/aikar/") {
        name = "aikar"
    }
    maven("https://maven.enginehub.org/repo/") {
        name = "enginehub"
    }
    maven {
        name = "plasmaServicesReleases"
        url = uri("https://maven.plasma.services/releases")
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
    
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("reflect"))
    implementation("com.github.cryptomorin:XSeries:13.7.1")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
    implementation("io.github.jordanfails:honey:1.1.2")
    // Unify exposes the official NBT-API types directly for entity, tile-entity,
    // and item NBT. The NBTAPI plugin supplies this dependency at runtime.
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.15.5")
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

fun com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.excludeKotlinRuntime() {
    // Kotlin is provided at runtime by Paper's plugin library loader (see plugin.yml).
    // Bundling it here causes LinkageErrors when other plugins call honey APIs across class loaders.
    exclude("kotlin/**")
    exclude("kotlinx/**")
    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/kotlin/**")
}

tasks {
    // Legacy shadow jar (Java 8 - for 1.8 to 1.16 servers)
    val shadowJarLegacy by registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        archiveBaseName.set("Unify")
        archiveClassifier.set("legacy")
        excludeKotlinRuntime()
        
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
        excludeKotlinRuntime()
    }
    
    // Default shadowJar uses legacy for compatibility
    shadowJar {
        archiveBaseName.set("Unify")
        archiveClassifier.set("")
        excludeKotlinRuntime()
    }

    build {
        dependsOn(shadowJar)
    }
}
