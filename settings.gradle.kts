pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // LiveKit의 audioswitch만 JitPack에서 받는다.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.davidliu") }
        }
    }
}

rootProject.name = "Collog-Android"
include(":app")
