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
        // CloudStream library, NiceHttp vb. buradan gelir
        maven("https://jitpack.io")
    }
}

rootProject.name = "StreamBox"
include(":app")
