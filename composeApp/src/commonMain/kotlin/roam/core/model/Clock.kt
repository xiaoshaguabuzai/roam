package roam.core.model

/** 平台时钟：commonMain 不依赖 JVM API，由各平台 actual 提供。 */
expect fun platformNow(): Long
