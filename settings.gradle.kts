rootProject.name = "revanced-rif-import-history"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // The ReVanced patcher is only published to GitHub Packages, which needs a token even for public packages.
        // Instead, the patcher API is taken from the public ReVanced CLI release jar (see patches/build.gradle.kts).
        ivy {
            name = "revancedCliReleases"
            url = uri("https://github.com/")
            patternLayout { artifact("[organisation]/[module]/releases/download/v[revision]/[module]-[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("ReVanced", "revanced-cli") }
        }
    }
}

include(":patches")
include(":extensions:rifimport")
