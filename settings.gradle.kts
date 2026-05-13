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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // Đã thêm Jitpack vì các Launcher thường dùng thư viện ngoài
    }
}

rootProject.name = "ZalithLauncher"

// NẾU THƯ MỤC CODE CHÍNH CỦA BẠN TÊN LÀ "app" (thường là vậy), HÃY ĐỂ LÀ include(":app").
// Nếu thư mục code chính thật sự tên là "ZalithLauncher", thì đổi lại thành include(":ZalithLauncher")
include(":app") 
include(":LWJGL")
include(":LayerController")
include(":ColorPicker")
include(":Terracotta")
