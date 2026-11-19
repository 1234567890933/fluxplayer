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

    fun setColorSettings(settings: ColorSettings) {
        controller?.setColorSettings(settings)
    }

    fun setHdrSwitch(enabled: Boolean) {
        controller?.setHdrSwitch(enabled)
    }
}
