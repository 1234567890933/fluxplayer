package dev.anilbeesetti.nextplayer.core.effect

import android.app.ActivityManager
import android.content.Context

/**
 * How much GPU work a phone can afford. The profiler answers this once per process from facts the
 * platform already exposes, and every enhancement decision is derived from it.
 */
enum class DeviceTier {
    LITE,
    BALANCED,
    ULTRA,
    ;

    companion object {
        /** Reads the two facts the tier needs, and never throws on an unusual device. */
        fun detect(context: Context): DeviceTier = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memory = ActivityManager.MemoryInfo()
            manager?.getMemoryInfo(memory)
            val ramMb = if (memory.totalMem > 0) memory.totalMem / (1024L * 1024L) else 2048L
            val glMajor = (manager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0x00030000) shr 16
            of(ramMb, glMajor)
        }.getOrDefault(LITE)

        /** Anything below 3 GB is treated as an entry device; it gets the cheapest pass. */
        private const val LOW_RAM_MB = 3072L

        /** 6 GB and a modern GL stack is where the full pipeline is safe. */
        private const val HIGH_RAM_MB = 6144L

        fun of(ramMb: Long, glEsMajor: Int): DeviceTier = when {
            ramMb < LOW_RAM_MB || glEsMajor < 3 -> LITE
            ramMb >= HIGH_RAM_MB && glEsMajor >= 3 -> ULTRA
            else -> BALANCED
        }
    }
}
