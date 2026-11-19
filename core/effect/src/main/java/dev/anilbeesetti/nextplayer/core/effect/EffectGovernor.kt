package dev.anilbeesetti.nextplayer.core.effect

/**
 * How much of the effect pipeline the device can afford at this moment.
 *
 * The three names match what the controller does with them: run the full grade, run the cheap
 * grade, or leave the picture alone.
 */
enum class EffectBudget {
    FULL,
    LIGHT,
    OFF,
}

/**
 * The breaker box for the effect pipeline.
 *
 * Everything that can go wrong with a GPU effect — the device is too hot, the battery saver is
 * on, the phone only has a gigabyte of memory, or the driver simply refused the last two
 * attempts — is folded into one decision: which [EffectBudget] the next frame gets. The
 * controller asks this class instead of guessing, so a weak device degrades in one place rather
 * than in five.
 *
 * The class is deliberately free of Android imports: it is pure Kotlin, so the unit tests can
 * cover every state transition without a device.
 */
class EffectGovernor {

    private var thermalLevel: ThermalLevel = ThermalLevel.NONE
    private var batterySaverOn: Boolean = false
    private var lowRamDevice: Boolean = false
    private var consecutiveFailures: Int = 0

    /** Set once, from ActivityManager.isLowRamDevice. */
    fun setLowRamDevice(value: Boolean) {
        lowRamDevice = value
    }

    fun onThermalLevel(level: ThermalLevel) {
        thermalLevel = level
    }

    fun onBatterySaver(enabled: Boolean) {
        batterySaverOn = enabled
    }

    fun onApplySucceeded() {
        consecutiveFailures = 0
    }

    /**
     * Two refusals in a row means this driver will not take the effects at all. Keeping on
     * trying would burn battery and heat for nothing, so the pipeline steps out of the way.
     */
    fun onApplyFailed() {
        consecutiveFailures += 1
    }

    val budget: EffectBudget
        get() = when {
            batterySaverOn -> EffectBudget.OFF
            consecutiveFailures >= 2 -> EffectBudget.OFF
            consecutiveFailures == 1 -> EffectBudget.LIGHT
            thermalLevel >= ThermalLevel.MODERATE -> EffectBudget.LIGHT
            lowRamDevice -> EffectBudget.LIGHT
            else -> EffectBudget.FULL
        }

    /**
     * How strong the HDR look should be, 0..1. A low-RAM phone gets a gentler grade so the frame
     * processor is not asked to push more pixels than it can, and a hot device backs off again.
     */
    val hdrStrength: Float
        get() = when {
            batterySaverOn -> 0f
            thermalLevel >= ThermalLevel.SEVERE -> 0.3f
            thermalLevel >= ThermalLevel.MODERATE -> 0.5f
            lowRamDevice -> 0.6f
            else -> 1f
        }

    /** True when the pipeline is currently holding back, for a UI that wants to say so. */
    val isConstrained: Boolean
        get() = budget != EffectBudget.FULL
}
