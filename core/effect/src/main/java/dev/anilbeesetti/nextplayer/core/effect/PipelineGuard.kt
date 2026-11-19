package dev.anilbeesetti.nextplayer.core.effect

/**
 * One place that decides whether a piece of GPU work is safe to attempt and remembers how it went.
 *
 * Every stage of the effect pipeline runs through here. A stage that throws is recorded and then
 * quietly skipped, because losing the grade is acceptable and losing playback is not. The counters
 * also feed the governor, so a device that keeps refusing work stops being asked.
 */
class PipelineGuard(private val maxFailuresPerStage: Int = 2) {

    private val failures = mutableMapOf<String, Int>()
    private val successes = mutableMapOf<String, Int>()

    /** True while this stage has not used up its failures. */
    fun isHealthy(stage: String): Boolean = (failures[stage] ?: 0) < maxFailuresPerStage

    /** Runs the block, records the outcome, and never throws. Returns null when it failed. */
    fun <T> attempt(stage: String, block: () -> T?): T? {
        if (!isHealthy(stage)) return null
        return runCatching { block() }
            .onSuccess {
                if (it != null) {
                    successes[stage] = (successes[stage] ?: 0) + 1
                }
            }
            .onFailure {
                failures[stage] = (failures[stage] ?: 0) + 1
            }
            .getOrNull()
    }

    fun failureCount(stage: String): Int = failures[stage] ?: 0

    fun successCount(stage: String): Int = successes[stage] ?: 0

    /** Called when the viewer asks for something new, so a previously broken stage gets another go. */
    fun reset() {
        failures.clear()
        successes.clear()
    }
}
