plugins {
    // declare plugin ids using full form (not kotlin("..."))
    id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
    id("com.gradleup.shadow") version "9.4.3" apply false
    id("maven-publish")
}

group = "me.jordanfails"
val unifyVersion = providers.gradleProperty("unifyVersion").get()
version = unifyVersion

subprojects {
    // apply kotlin plugin properly
    plugins.apply("org.jetbrains.kotlin.jvm")

    // Unify's own modules use its internals and beta features freely; dependents get an error or warning.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.optIn.addAll("me.jordanfails.unify.UnifyInternal", "me.jordanfails.unify.Beta")
    }

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

// Define paths to all jars we want to bundle
// Legacy JARs (Java 8 compatible: 1.8, 1.9, 1.12, 1.16)
val legacyCoreJar = file("unify-core/build/libs/Unify-$unifyVersion-legacy.jar")
val nms_v1_8_R3 = file("unify-nms/unify-nms-v1_8_R3/build/libs/unify-nms-v1_8_R3-$unifyVersion.jar")
val nms_v1_9_R2 = file("unify-nms/unify-nms-v1_9_R2/build/libs/unify-nms-v1_9_R2-$unifyVersion.jar")
val nms_v1_12_R1 = file("unify-nms/unify-nms-v1_12_R1/build/libs/unify-nms-v1_12_R1-$unifyVersion.jar")
val nms_v1_16_R3 = file("unify-nms/unify-nms-v1_16_R3/build/libs/unify-nms-v1_16_R3-$unifyVersion.jar")

// Modern JARs (Paper 1.20.6+: 1.20.6, 1.21.x, 26.x)
val modernCoreJar = file("unify-core/build/libs/Unify-$unifyVersion-modern.jar")
val nms_v1_20_R4 = file("unify-nms/unify-nms-v1_20_R4/build/libs/unify-nms-v1_20_R4-$unifyVersion.jar")
// Must match what the module's `jar` task actually produces. This pointed at a stale
// `-dev` artifact, so every change to the 1.21 handler was silently left out of the bundle.
val nms_v1_21_R1 = file("unify-nms/unify-nms-v1_21_R1/build/libs/unify-nms-v1_21_R1-$unifyVersion.jar")
val nms_v26_R1 = file("unify-nms/unify-nms-v26_R1/build/libs/unify-nms-v26_R1-$unifyVersion.jar")

// Task to build legacy modules (Java 8)
tasks.register("buildLegacy") {
    group = "build"
    description = "Build legacy modules (Java 8 - for 1.8 to 1.16 servers)"
    dependsOn(":unify-core:shadowJarLegacy")
    dependsOn(":unify-nms:unify-nms-v1_8_R3:jar")
    dependsOn(":unify-nms:unify-nms-v1_9_R2:jar")
    dependsOn(":unify-nms:unify-nms-v1_12_R1:jar")
    dependsOn(":unify-nms:unify-nms-v1_16_R3:jar")
}

// Task to build modern modules (Java 17+)
tasks.register("buildModern") {
    group = "build"
    description = "Build modern modules (Paper 1.20.6+)"
    dependsOn(":unify-core:shadowJarModern")
    dependsOn(":unify-nms:unify-nms-v1_16_R3:jar")
    dependsOn(":unify-nms:unify-nms-v1_20_R4:jar")
    dependsOn(":unify-nms:unify-nms-v1_21_R1:jar")
    dependsOn(":unify-nms:unify-nms-v26_R1:jar")
}

// Missing input jars fail the build (zipTree on a missing file) so a release can't silently lose a handler.
// Legacy bundled plugin (Java 8 - for 1.8 to 1.16 servers)
val bundledLegacyTask = tasks.register<Jar>("bundledLegacy") {
    group = "build"
    description = "Creates the legacy Unify plugin JAR (Java 8 - for 1.8 to 1.16 servers)"
    
    dependsOn("buildLegacy")
    mustRunAfter("buildLegacy")
    
    // Unify-<version>-legacy.jar: Java 8 build for 1.8-1.16 servers.
    archiveBaseName.set("Unify")
    archiveVersion.set(unifyVersion)
    archiveClassifier.set("legacy")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    from({ zipTree(legacyCoreJar) })
    from({ zipTree(nms_v1_8_R3) })
    from({ zipTree(nms_v1_9_R2) })
    from({ zipTree(nms_v1_12_R1) })
    from({ zipTree(nms_v1_16_R3) })
}

// Modern bundled plugin (Paper 1.20.6+)
val bundledModernTask = tasks.register<Jar>("bundledModern") {
    group = "build"
    description = "Creates the modern Unify plugin JAR (Paper 1.20.6+)"
    
    dependsOn("buildModern")
    mustRunAfter("buildModern")
    
    // Unify-<version>-modern.jar: Paper 1.20.6+. Plugins should compile against this one.
    archiveBaseName.set("Unify")
    archiveVersion.set(unifyVersion)
    archiveClassifier.set("modern")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    // NMS handlers are Mojang-mapped; without this Paper 1.20.5+ would try to remap them as Spigot-mapped.
    manifest { attributes["paperweight-mappings-namespace"] = "mojang" }

    from({ zipTree(modernCoreJar) })
    from({ zipTree(nms_v1_16_R3) })
    from({ zipTree(nms_v1_20_R4) })
    from({ zipTree(nms_v1_21_R1) })
    from({ zipTree(nms_v26_R1) })
}

// Sources let IDEs show Unify's KDoc and step into its code from a dependent plugin.
val sourcesJarTask = tasks.register<Jar>("sourcesJar") {
    archiveBaseName.set("Unify")
    archiveVersion.set(unifyVersion)
    archiveClassifier.set("sources")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from("unify-core/src/main/kotlin", "unify-core/src/main/java", "unify-core/src/main/resources")
}

// The bundled jars already contain ACF, XSeries and Adventure, so the POMs only list Kotlin.
publishing {
    publications {
        listOf("legacy" to bundledLegacyTask, "modern" to bundledModernTask).forEach { (flavor, jar) ->
            create<MavenPublication>(flavor) {
                groupId = "me.jordanfails"
                artifactId = "unify-$flavor"
                version = unifyVersion
                artifact(jar) { classifier = null }
                artifact(sourcesJarTask) { classifier = "sources" }
                pom {
                    // The modern jar doesn't bundle Kotlin (the legacy one does); either way Java callers
                    // need Kotlin's types on their compile classpath for Unify's lambda APIs.
                    withXml {
                        val dependency = asNode().appendNode("dependencies").appendNode("dependency")
                        dependency.appendNode("groupId", "org.jetbrains.kotlin")
                        dependency.appendNode("artifactId", "kotlin-stdlib")
                        dependency.appendNode("version", "2.2.21")
                        dependency.appendNode("scope", "compile")
                    }
                    name.set("Unify ($flavor)")
                    description.set("All-in-one utility plugin for building Minecraft plugins in Kotlin and Java.")
                    url.set("https://github.com/JordanFails/Unify")
                }
            }
        }
    }
    repositories {
        mavenLocal()
        // Set unifyRepoUrl/unifyRepoUser/unifyRepoPassword (e.g. in ~/.gradle/gradle.properties) to publish remotely.
        providers.gradleProperty("unifyRepoUrl").orNull?.let { repoUrl ->
            maven {
                name = "unify"
                url = uri(repoUrl)
                credentials {
                    username = providers.gradleProperty("unifyRepoUser").orNull
                    password = providers.gradleProperty("unifyRepoPassword").orNull
                }
            }
        }
    }
}

// Build both versions and publish to maven local
tasks.register("bundledAll") {
    group = "build"
    description = "Build both legacy and modern bundled plugins and publish to Maven Local"
    dependsOn("bundledLegacy")
    dependsOn("bundledModern")
    finalizedBy("publishToMavenLocal")
}