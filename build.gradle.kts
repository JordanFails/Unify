plugins {
    // declare plugin ids using full form (not kotlin("..."))
    id("org.jetbrains.kotlin.jvm") version "1.9.22" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

subprojects {
    // apply kotlin plugin properly
    plugins.apply("org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    // use typed access to the Kotlin extension
    the<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>().apply {
        jvmToolchain(21)
    }
}

// Define paths to all jars we want to bundle
val coreJar = file("unify-core/build/libs/Unify-1.0-SNAPSHOT.jar")
val nms_v1_8_R3 = file("unify-nms/unify-nms-v1_8_R3/build/libs/unify-nms-v1_8_R3-1.0-SNAPSHOT.jar")
val nms_v1_9_R2 = file("unify-nms/unify-nms-v1_9_R2/build/libs/unify-nms-v1_9_R2-1.0-SNAPSHOT.jar")
val nms_v1_12_R1 = file("unify-nms/unify-nms-v1_12_R1/build/libs/unify-nms-v1_12_R1-1.0-SNAPSHOT.jar")
val nms_v1_16_R3 = file("unify-nms/unify-nms-v1_16_R3/build/libs/unify-nms-v1_16_R3-1.0-SNAPSHOT.jar")
val nms_v1_20_R4 = file("unify-nms/unify-nms-v1_20_R4/build/libs/unify-nms-v1_20_R4-1.0-SNAPSHOT.jar")
val nms_v1_21_R1 = file("unify-nms/unify-nms-v1_21_R1/build/libs/unify-nms-v1_21_R1-1.0-SNAPSHOT.jar")

// Task to build everything first
tasks.register("buildAll") {
    group = "build"
    description = "Build all modules"
    dependsOn(":unify-core:shadowJar")
    dependsOn(":unify-nms:unify-nms-v1_8_R3:jar")
    dependsOn(":unify-nms:unify-nms-v1_9_R2:jar")
    dependsOn(":unify-nms:unify-nms-v1_12_R1:jar")
    dependsOn(":unify-nms:unify-nms-v1_16_R3:jar")
    dependsOn(":unify-nms:unify-nms-v1_20_R4:jar")
    dependsOn(":unify-nms:unify-nms-v1_21_R1:jar")
}

// Task to bundle everything after buildAll completes
tasks.register<Jar>("bundledPlugin") {
    group = "build"
    description = "Creates the final Unify plugin JAR with all NMS handlers bundled"
    
    // Make this run after buildAll
    dependsOn("buildAll")
    mustRunAfter("buildAll")
    
    archiveBaseName.set("Unify")
    archiveVersion.set("1.0-SNAPSHOT")
    archiveClassifier.set("bundled")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    
    // Handle duplicate entries by taking the first one
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    // These from() calls are deferred using providers
    from({ if (coreJar.exists()) zipTree(coreJar) else emptyList<Any>() })
    from({ if (nms_v1_8_R3.exists()) zipTree(nms_v1_8_R3) else emptyList<Any>() })
    from({ if (nms_v1_9_R2.exists()) zipTree(nms_v1_9_R2) else emptyList<Any>() })
    from({ if (nms_v1_12_R1.exists()) zipTree(nms_v1_12_R1) else emptyList<Any>() })
    from({ if (nms_v1_16_R3.exists()) zipTree(nms_v1_16_R3) else emptyList<Any>() })
    from({ if (nms_v1_20_R4.exists()) zipTree(nms_v1_20_R4) else emptyList<Any>() })
    from({ if (nms_v1_21_R1.exists()) zipTree(nms_v1_21_R1) else emptyList<Any>() })
}