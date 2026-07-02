plugins {
    // declare plugin ids using full form (not kotlin("..."))
    id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("maven-publish")
}

group = "me.jordanfails"
version = "1.0-SNAPSHOT"

subprojects {
    // apply kotlin plugin properly
    plugins.apply("org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

// Define paths to all jars we want to bundle
// Legacy JARs (Java 8 compatible: 1.8, 1.9, 1.12, 1.16)
val legacyCoreJar = file("unify-core/build/libs/Unify-1.0-SNAPSHOT-legacy.jar")
val nms_v1_8_R3 = file("unify-nms/unify-nms-v1_8_R3/build/libs/unify-nms-v1_8_R3-1.0-SNAPSHOT.jar")
val nms_v1_9_R2 = file("unify-nms/unify-nms-v1_9_R2/build/libs/unify-nms-v1_9_R2-1.0-SNAPSHOT.jar")
val nms_v1_12_R1 = file("unify-nms/unify-nms-v1_12_R1/build/libs/unify-nms-v1_12_R1-1.0-SNAPSHOT.jar")
val nms_v1_16_R3 = file("unify-nms/unify-nms-v1_16_R3/build/libs/unify-nms-v1_16_R3-1.0-SNAPSHOT.jar")

// Modern JARs (Java 17+ compatible: 1.20, 1.21)
val modernCoreJar = file("unify-core/build/libs/Unify-1.0-SNAPSHOT-modern.jar")
val nms_v1_20_R4 = file("unify-nms/unify-nms-v1_20_R4/build/libs/unify-nms-v1_20_R4-1.0-SNAPSHOT.jar")
val nms_v1_21_R1 = file("unify-nms/unify-nms-v1_21_R1/build/libs/unify-nms-v1_21_R1-1.0-SNAPSHOT-dev.jar")

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
    description = "Build modern modules (Java 17+ - for 1.17+ servers)"
    dependsOn(":unify-core:shadowJarModern")
    dependsOn(":unify-nms:unify-nms-v1_16_R3:jar")
    dependsOn(":unify-nms:unify-nms-v1_20_R4:jar")
    dependsOn(":unify-nms:unify-nms-v1_21_R1:jar")
}

// Legacy bundled plugin (Java 8 - for 1.8 to 1.16 servers)
val bundledLegacyTask = tasks.register<Jar>("bundledLegacy") {
    group = "build"
    description = "Creates the legacy Unify plugin JAR (Java 8 - for 1.8 to 1.16 servers)"
    
    dependsOn("buildLegacy")
    mustRunAfter("buildLegacy")
    
    archiveBaseName.set("Unify")
    archiveVersion.set("1.0-SNAPSHOT")
    archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("libs/legacy"))
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    from({ if (legacyCoreJar.exists()) zipTree(legacyCoreJar) else emptyList<Any>() })
    from({ if (nms_v1_8_R3.exists()) zipTree(nms_v1_8_R3) else emptyList<Any>() })
    from({ if (nms_v1_9_R2.exists()) zipTree(nms_v1_9_R2) else emptyList<Any>() })
    from({ if (nms_v1_12_R1.exists()) zipTree(nms_v1_12_R1) else emptyList<Any>() })
    from({ if (nms_v1_16_R3.exists()) zipTree(nms_v1_16_R3) else emptyList<Any>() })
}

// Modern bundled plugin (Java 17+ - for 1.17+ servers)
val bundledModernTask = tasks.register<Jar>("bundledModern") {
    group = "build"
    description = "Creates the modern Unify plugin JAR (Java 17+ - for 1.17+ servers)"
    
    dependsOn("buildModern")
    mustRunAfter("buildModern")
    
    archiveBaseName.set("Unify")
    archiveVersion.set("1.0-SNAPSHOT")
    archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("libs/modern"))
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    from({ if (modernCoreJar.exists()) zipTree(modernCoreJar) else emptyList<Any>() })
    from({ if (nms_v1_16_R3.exists()) zipTree(nms_v1_16_R3) else emptyList<Any>() })
    from({ if (nms_v1_20_R4.exists()) zipTree(nms_v1_20_R4) else emptyList<Any>() })
    from({ if (nms_v1_21_R1.exists()) zipTree(nms_v1_21_R1) else emptyList<Any>() })
}

// Publishing configuration
publishing {
    publications {
        create<MavenPublication>("legacy") {
            groupId = "me.jordanfails"
            artifactId = "unify-legacy"
            version = "1.0-SNAPSHOT"
            artifact(bundledLegacyTask)
        }
        create<MavenPublication>("modern") {
            groupId = "me.jordanfails"
            artifactId = "unify-modern"
            version = "1.0-SNAPSHOT"
            artifact(bundledModernTask)
        }
    }
    repositories {
        mavenLocal()
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