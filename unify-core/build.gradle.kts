plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.4.3"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "me.jordanfails"
version = providers.gradleProperty("unifyVersion").get()

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

// Bundled into the legacy jar only; modern servers load these through plugin.yml `libraries`.
val legacyRuntime: Configuration by configurations.creating

dependencies {
    legacyRuntime(kotlin("stdlib"))
    legacyRuntime(kotlin("reflect"))
    legacyRuntime("com.google.code.gson:gson:2.14.0")
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
    implementation("io.github.jordanfails:honey:1.1.4-SNAPSHOT")
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
    // Only the Kotlin runtime's own module metadata. Unify's `unify-core.kotlin_module` MUST stay:
    // without it, consumers can still see our classes but NOT a single top-level declaration from
    // this module — the menu DSL's `button { }` / `filler()` and every top-level extension become
    // unresolvable at compile time. A blanket `META-INF/*.kotlin_module` exclude hid the whole DSL.
    exclude("META-INF/kotlin-*.kotlin_module")
    exclude("META-INF/kotlinx-*.kotlin_module")
    exclude("META-INF/kotlin/**")
}

tasks {
    processResources {
        val pluginVersion = project.version.toString()
        inputs.property("version", pluginVersion)
        filesMatching("plugin.yml") { expand("version" to pluginVersion) }
    }

    // Legacy shadow jar (Java 8 - for 1.8 to 1.16 servers). Those servers have no plugin library
    // loader, so Kotlin and gson ship inside the jar and plugin.yml drops its `libraries` block.
    val shadowJarLegacy by registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get(), legacyRuntime)
        archiveBaseName.set("Unify")
        archiveClassifier.set("legacy")
        // The Mongo driver's record codec is Java 17 bytecode; nothing on a Java 8 server can use it.
        exclude("org/bson/codecs/record/**")

        filesMatching("plugin.yml") {
            var inLibraries = false
            filter { line ->
                when {
                    // 1.13-1.16 reject newer api-versions; 1.8-1.12 ignore the key.
                    line.startsWith("api-version:") -> "api-version: '1.13'"
                    line.startsWith("libraries:") -> { inLibraries = true; "" }
                    inLibraries && line.startsWith("  -") -> ""
                    else -> { inLibraries = false; line }
                }
            }
        }

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
    
    // Plain shadowJar has the modern layout (no Kotlin, `libraries` in plugin.yml). Release jars come from
    // the root project's bundledLegacy/bundledModern tasks, which also add the NMS handlers.
    shadowJar {
        archiveBaseName.set("Unify")
        archiveClassifier.set("")
        excludeKotlinRuntime()
    }

    build {
        dependsOn(shadowJar)
    }
}
