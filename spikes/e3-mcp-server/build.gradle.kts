// E3：验证官方 MCP Kotlin SDK 能否在「Android 同构的 JVM 环境」里跑通
// - 起一个 Streamable HTTP MCP Server（127.0.0.1:8765/mcp）
// - 用官方 Client 自连，list/call 4 个只读工具
// - 同时留出 HTTP 端点，便于 curl / 外部 MCP 客户端复验
//
// 说明：这里用 buildscript + apply 而不是 plugins {} 块，避免依赖 Gradle Plugin Portal
//（国内网络访问 plugins.gradle.org 不稳定），Kotlin 插件直接从 Maven Central 取。
import org.gradle.api.plugins.JavaApplication
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21") }
}

apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "application")

repositories { mavenCentral() }

dependencies {
    add("implementation", "io.modelcontextprotocol:kotlin-sdk-server:0.9.0")
    add("implementation", "io.modelcontextprotocol:kotlin-sdk-client:0.9.0")
    add("implementation", "io.ktor:ktor-server-cio:3.2.3")
    add("implementation", "io.ktor:ktor-server-content-negotiation:3.2.3")
    add("implementation", "io.ktor:ktor-serialization-kotlinx-json:3.2.3")
    add("implementation", "io.ktor:ktor-client-cio:3.2.3")
    add("implementation", "org.slf4j:slf4j-simple:2.0.16")
}

configure<KotlinJvmProjectExtension> {
    jvmToolchain(21)
}

configure<JavaApplication> {
    mainClass.set("roam.spike.MainKt")
}

tasks.withType<JavaExec>().configureEach {
    // 让实验输出可读，别被 SDK 的 debug 日志淹没
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
    jvmArgs("-Dfile.encoding=UTF-8")
}
