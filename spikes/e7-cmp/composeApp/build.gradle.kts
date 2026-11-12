// E5：Compose Multiplatform 冒烟工程
// 验证目标：同一套 commonMain 代码能同时编译出 Android APK 与 Desktop 可执行程序，
// 这是「Android + 桌面共享一套 UI/业务」这一技术路线能否落地的最小证明。
plugins {
    kotlin("multiplatform") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("org.jetbrains.compose") version "1.9.3"
    id("com.android.application") version "8.13.2"
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        val desktopMain by getting {
            dependencies { implementation(compose.desktop.currentOs) }
        }
        val androidMain by getting {
            dependencies { implementation("androidx.activity:activity-compose:1.10.1") }
        }
    }
}

android {
    namespace = "roam.cmp"
    compileSdk = 36

    defaultConfig {
        applicationId = "roam.cmp"
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
        mainClass = "roam.cmp.MainKt"
        nativeDistributions {
            // 只产出 app-image，避免触发 WiX 下载（国内网络拉 GitHub release 不稳）
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage)
        }
    }
}
