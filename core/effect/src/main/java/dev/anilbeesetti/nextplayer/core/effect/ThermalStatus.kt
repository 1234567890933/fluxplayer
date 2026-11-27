package dev.anilbeesetti.nextplayer.core.effect

/**
 * Maps the platform's thermal status to the scale the pipeline understands.
 *
 * PowerManager reports six steps; the pipeline only needs five, and the two hottest platform
 * steps are both "stop doing extra work", so SEVERE and CRITICAL collapse them.
 */
object ThermalStatus {

    /** Platform values, repeated here so the mapping can be tested without Android. */
    const val NONE = 0
    const val LIGHT = 1
    const val MODERATE = 2
    const val SEVERE = 3
    const val CRITICAL = 4
    const val EMERGENCY = 5
    const val SHUTDOWN = 6

    fun toThermalLevel(status: Int): ThermalLevel = when {
        status <= NONE -> ThermalLevel.NONE
        status == LIGHT -> ThermalLevel.LIGHT
        status == MODERATE -> ThermalLevel.MODERATE
        status == SEVERE -> ThermalLevel.SEVERE
        else -> ThermalLevel.CRITICAL
    }
}

/** The extension the service calls: a raw PowerManager status becomes a ThermalLevel. */
fun Int.toThermalLevel(): ThermalLevel = ThermalStatus.toThermalLevel(this)
