/**
 * 平台时钟实现（Android）。
 */
package roam.core.model

actual fun platformNow(): Long = System.currentTimeMillis()
