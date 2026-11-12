// 根工程不做业务配置：插件与依赖在 :composeApp 中声明。
// 版本必须成组升级：Kotlin 2.2.21 ↔ compose 编译器插件 2.2.21 ↔ CMP 1.9.3 ↔ AGP 8.13.2。

subprojects {
    buildscript {
        // 某些 Gradle / Android Studio 组合下，Kotlin DSL 会把 org.jetbrains:annotations
        // 钉在 13.0（"Pinned to the embedded Kotlin"），而 AGP 8.13 的传递依赖要求 23.0.0，
        // 于是插件 classpath 解析失败。这里统一抬到 23.0.0（向下兼容）。
        configurations.configureEach {
            resolutionStrategy.force("org.jetbrains:annotations:23.0.0")
        }
    }
}
