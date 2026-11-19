package dev.anilbeesetti.nextplayer.core.effect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** How much of the effect pipeline the battery allows. */
enum class PowerMode {
    NORMAL,
    SAVER,
}

/**
 * Watches the battery broadcast and reports when the device drops into saver mode:
 * below 20 % and not charging. The caller decides what to switch off.
 */
class BatteryGuard(private val context: Context, private val onModeChanged: (PowerMode) -> Unit) {
    private var receiver: BroadcastReceiver? = null
    private var lastMode: PowerMode = PowerMode.NORMAL

    fun start() {
        if (receiver != null) return
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                publish(modeOf(intent))
            }
        }
        receiver = batteryReceiver
        context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
    }

    fun stop() {
        receiver?.let { registered ->
            runCatching { context.unregisterReceiver(registered) }
        }
        receiver = null
    }

    private fun modeOf(intent: Intent): PowerMode {
        val level = intent.getIntExtra(
            BatteryManager.EXTRA_LEVEL,
            -1,
        )
        val scale = intent.getIntExtra(
            BatteryManager.EXTRA_SCALE,
            -1,
        )
        val plugged = intent.getIntExtra(
            BatteryManager.EXTRA_PLUGGED,
            0,
        )
        if (level < 0 || scale <= 0) return PowerMode.NORMAL
        val percent = level * 100 / scale
        return if (percent < SAVER_THRESHOLD_PERCENT && plugged == 0) {
            PowerMode.SAVER
        } else {
            PowerMode.NORMAL
        }
    }

    private fun publish(mode: PowerMode) {
        if (mode == lastMode) return
        lastMode = mode
        onModeChanged(mode)
    }

    companion object {
        const val SAVER_THRESHOLD_PERCENT = 20
    }
}
