package dev.anilbeesetti.nextplayer.core.effect

import android.app.ActivityManager
import android.content.Context

/**
 * Answers two questions: what tier is this phone, and how strong should AUTO be on it.
 * The second one is pure arithmetic, so it can be checked without a device.
 */
object DeviceTierDetector {

    fun detect(context: Context): DeviceTier = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(memory)
        val ramMb = if (memory.totalMem > 0) memory.totalMem / (1024L * 1024L) else 2048L
        val glMajor = (manager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0x00030000) shr 16
        DeviceTier.of(ramMb, glMajor)
    }.getOrDefault(DeviceTier.LITE)

    /** How hard AUTO should push on this tier: the weaker the phone, the gentler the grade. */
    fun autoStrength(tier: DeviceTier): Float = when (tier) {
        DeviceTier.LITE -> 0.28f
        DeviceTier.BALANCED -> 0.55f
        DeviceTier.ULTRA -> 0.85f
    }
}
