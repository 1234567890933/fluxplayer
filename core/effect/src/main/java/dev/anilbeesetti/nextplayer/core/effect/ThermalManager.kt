package dev.anilbeesetti.nextplayer.core.effect

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import java.io.File
import java.util.concurrent.Executor

/**
 * How hot the device is, mapped to what the effect pipeline is allowed to do.
 * The scale is applied to every colour-grade parameter, so quality drops smoothly
 * instead of switching off in one step.
 */
enum class ThermalLevel {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    ;

    /** Multiplier for effect strength: 1 = full quality, 0 = effects off. */
    val effectScale: Float
        get() = when (this) {
            NONE, LIGHT -> 1f
            MODERATE -> 0.75f
            SEVERE, CRITICAL -> 0f
        }

    val pausesPlayback: Boolean
        get() = this == CRITICAL

    val showsWarning: Boolean
        get() = this == SEVERE || this == CRITICAL
}

/**
 * Watches device thermals and reports level changes.
 *
 * API 29 and above uses PowerManager's thermal status listener, which is the signal the
 * platform itself uses. Below that it polls the first thermal zone every 30 seconds.
 */
class ThermalManager(
    private val context: Context,
    private val onLevelChanged: (ThermalLevel) -> Unit,
) {
    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private var statusListener: PowerManager.OnThermalStatusChangedListener? = null
    private var pollThread: Thread? = null

    @Volatile
    private var polling = false

    private var lastLevel: ThermalLevel = ThermalLevel.NONE

    fun start() {
        // Local copy: a smart cast on the class property does not survive into the runCatching
        // lambdas below, so the nullable receiver would leak into the lambda body.
        val pm = powerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
            // Two guards, both learned the hard way:
            //  * registering twice leaks a listener, and once the platform's per-process limit
            //    is reached addThermalStatusListener throws RuntimeException("Listener failed
            //    to set") — which used to crash PlayerService.onCreate;
            //  * so a failed registration falls back to polling instead of taking the service
            //    down with it.
            if (statusListener != null) return
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                publish(levelOf(status))
            }
            val registered = runCatching {
                pm.addThermalStatusListener(
                    mainExecutor(),
                    listener,
                )
            }.isSuccess
            if (registered) {
                statusListener = listener
                runCatching { publish(levelOf(pm.currentThermalStatus)) }
            } else {
                Log.w(
                    TAG,
                    "thermal listener unavailable, falling back to polling",
                )
                startPolling()
            }
        } else {
            startPolling()
        }
    }

    fun stop() {
        val listener = statusListener
        if (listener != null && powerManager != null) {
            powerManager.removeThermalStatusListener(listener)
        }
        statusListener = null
        polling = false
        pollThread?.interrupt()
        pollThread = null
    }

    private fun levelOf(status: Int): ThermalLevel = when (status) {
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
        else -> ThermalLevel.NONE
    }

    private fun publish(level: ThermalLevel) {
        if (level == lastLevel) return
        lastLevel = level
        onLevelChanged(level)
    }

    private fun startPolling() {
        polling = true
        pollThread = Thread {
            while (polling) {
                val celsius = readZoneCelsius()
                if (celsius != null) {
                    publish(
                        when {
                            celsius >= 50f -> ThermalLevel.CRITICAL
                            celsius >= 45f -> ThermalLevel.SEVERE
                            celsius >= 40f -> ThermalLevel.MODERATE
                            else -> ThermalLevel.NONE
                        },
                    )
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (interrupted: InterruptedException) {
                    return@Thread
                }
            }
        }.apply {
            name = "fluxplayer-thermal"
            isDaemon = true
            start()
        }
    }

    private fun readZoneCelsius(): Float? = runCatching {
        File(THERMAL_ZONE_PATH).readText().trim().toFloat() / 1000f
    }.getOrNull()

    private fun mainExecutor(): Executor {
        val handler = Handler(Looper.getMainLooper())
        return Executor { command -> handler.post(command) }
    }

    private companion object {
        private const val TAG = "ThermalManager"
        const val POLL_INTERVAL_MS = 30_000L
        const val THERMAL_ZONE_PATH = "/sys/class/thermal/thermal_zone0/temp"
    }
}
