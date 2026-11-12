pluginManagement {
    // 涓嶄緷璧?Gradle Plugin Portal锛堝浗鍐呯綉缁滀笉绋筹級锛屾彃浠舵爣璁板湪 Maven Central / Google Maven 閮芥湁
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "e7-cmp"
include(":composeApp")

