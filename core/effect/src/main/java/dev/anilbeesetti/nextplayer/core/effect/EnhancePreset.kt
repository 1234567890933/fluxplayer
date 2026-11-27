package dev.anilbeesetti.nextplayer.core.effect

/**
 * What the viewer asked the enhancement engine for. AUTO lets the device profiler decide the
 * strength, the other three pin it. OFF hands the frames back untouched.
 */
enum class EnhancePreset {
    OFF,
    AUTO,
    SUBTLE,
    BALANCED,
    VIVID,
    MAX,
    ;

    val label: String
        get() = when (this) {
            OFF -> "Enhance off"
            AUTO -> "Enhance auto"
            SUBTLE -> "Enhance subtle"
            BALANCED -> "Enhance balanced"
            VIVID -> "Enhance vivid"
            MAX -> "Enhance max"
        }

    /** The next preset when the chip is tapped, so one button can reach every state. */
    fun next(): EnhancePreset = when (this) {
        OFF -> AUTO
        AUTO -> SUBTLE
        SUBTLE -> BALANCED
        BALANCED -> VIVID
        VIVID -> MAX
        MAX -> OFF
    }

    companion object {
        fun of(name: String?): EnhancePreset =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTO
    }
}
