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
        exclusiveContent {
            forRepository {
                maven("https://maven.mozilla.org/maven2")
            }
            filter {
                includeGroup("org.mozilla.geckoview")
                includeGroup("org.mozilla.components")
            }
        }
    }
}

rootProject.name = "minibrowser"
include(":app", ":benchmark")
