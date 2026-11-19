package dev.anilbeesetti.nextplayer.core.effect

/**
 * Lets the player UI reach the effects controller that [PlayerService] owns.
 *
 * The controller needs an ExoPlayer, so it is created inside the service and cannot be injected
 * into a composable. Rather than plumbing a new dependency through the whole DI graph for two
 * setters, the service registers itself here while it is alive and the UI calls through the
 * bridge. Every call is a no-op when no player is attached, which is exactly what should happen
 * when the sheet is open but playback has stopped.
 */
object PlayerEffectsBridge {

    @Volatile
    private var controller: PlayerEffectsController? = null

    /** True while a player is attached, so the UI can grey out the controls. */
    val isAvailable: Boolean
        get() = controller != null

    fun register(controller: PlayerEffectsController) {
        this.controller = controller
    }

    fun unregister(controller: PlayerEffectsController) {
        if (this.controller === controller) {
            this.controller = null
        }
    }

    /**
     * Returns true only when a live controller took the call. The UI uses this to decide whether
     * to show the switch as on: a button that lights up while nothing happens is a lie, and this
     * is what stops that.
     */
    fun setColorSettings(settings: ColorSettings): Boolean =
        controller?.let { runCatching { it.setColorSettings(settings) }.isSuccess } ?: false

    fun setHdrSwitch(enabled: Boolean): Boolean =
        controller?.let { runCatching { it.setHdrSwitch(enabled) }.isSuccess } ?: false
}
