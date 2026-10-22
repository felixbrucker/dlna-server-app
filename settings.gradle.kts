pluginManagement {
    repositories {
        google()
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://plugins.gradle.org/m2/") }
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
}

rootProject.name = "dlnaserver"
include(":app")
