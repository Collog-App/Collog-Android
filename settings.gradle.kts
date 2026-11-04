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
        // 카카오 SDK 는 카카오 저장소에서만 받는다.
        maven("https://devrepo.kakao.com/nexus/content/groups/public/") {
            content { includeGroup("com.kakao.sdk") }
        }
    }
}

rootProject.name = "Collog-Android"
include(":app")
