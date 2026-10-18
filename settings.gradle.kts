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
    }
}

rootProject.name = "bertopia"

include(":domain")
include(":anki")
include(":cardRender")
include(":composeApp")
include(":androidApp")
include(":desktopApp")
include(":catalogTool")
include(":cli")
