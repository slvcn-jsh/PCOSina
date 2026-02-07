import java.io.File

val configuredAndroidHome = System.getProperty("android.user.home")
val resolvedAndroidHome = if (configuredAndroidHome.isNullOrBlank() || !File(configuredAndroidHome).isAbsolute) {
    rootDir.resolve(".android").absoluteFile
} else {
    File(configuredAndroidHome)
}
if (!resolvedAndroidHome.exists()) {
    resolvedAndroidHome.mkdirs()
}
System.setProperty("android.user.home", resolvedAndroidHome.absolutePath)

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "PCOSINA"
include(":app")
