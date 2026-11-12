// Roam · Compose Multiplatform 全栈 Agent
//
// 版本必须成组升级：Kotlin 2.2.21 ↔ Compose 编译器插件 2.2.21 ↔ CMP 1.9.3。
plugins {
    kotlin("multiplatform") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("org.jetbrains.compose") version "1.9.3"
    id("com.android.application") version "8.13.2"
    id("app.cash.sqldelight") version "2.4.1"
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("app.cash.sqldelight:runtime:2.4.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.10.1")
                implementation("app.cash.sqldelight:android-driver:2.4.1")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("app.cash.sqldelight:sqlite-driver:2.4.1")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

sqldelight {
    databases {
        create("RoamDatabase") {
            packageName.set("roam.db")
        }
    }
}

android {
    namespace = "roam.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "roam.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "roam.MainKt"
    }
}

// IDE（IntelliJ / Android Studio）的 KMP 运行配置走的是 Kotlin 插件的 desktopRun，
// 它默认没有 mainClass，会报 "No main class specified"。这里补齐，使两种入口一致。
// 该任务由 Kotlin 插件延迟注册，因此用 configureEach + 名称匹配，避免触发 API 内部类。
tasks.configureEach {
    if (name == "desktopRun" && this is JavaExec) {
        workingDir = rootProject.projectDir
        doFirst { mainClass.set("roam.MainKt") }
    }
}
