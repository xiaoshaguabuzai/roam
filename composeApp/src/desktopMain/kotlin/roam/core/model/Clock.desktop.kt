/**
 * 平台时钟实现（桌面）。
 */
package roam.core.model

actual fun platformNow(): Long = System.currentTimeMillis()
