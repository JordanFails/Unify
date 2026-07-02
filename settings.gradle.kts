pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "Unify"

// Honey is not on Maven Central yet; use a composite build when the repo is available locally or in CI.
fun findHoneyBuild(): java.io.File? {
    val candidates = listOf(
        file("honey"),
        file("../honey"),
    )
    return candidates.firstOrNull { it.isDirectory && it.resolve("settings.gradle.kts").exists() }
}

findHoneyBuild()?.let { honeyDir ->
    includeBuild(honeyDir) {
        dependencySubstitution {
            substitute(module("io.github.jordanfails:honey")).using(project(":"))
        }
    }
}

include("unify-core")
include("unify-nms:unify-nms-v1_8_R3")
include("unify-nms:unify-nms-v1_9_R2")
include("unify-nms:unify-nms-v1_12_R1")
include("unify-nms:unify-nms-v1_16_R3")
include("unify-nms:unify-nms-v1_20_R4")
include("unify-nms:unify-nms-v1_21_R1")