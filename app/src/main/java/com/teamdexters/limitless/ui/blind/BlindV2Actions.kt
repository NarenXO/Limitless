package com.teamdexters.limitless.ui.blind

/**
 * Public API for Hazel integration to trigger Blind Assist v2 actions.
 * Hazel can call these functions when receiving wake phrases or intents.
 */
object BlindV2Actions {

    private var sceneNarrationCallback: (() -> Unit)? = null

    /**
     * Register the callback for scene narration requests.
     * Called by BlindHomeScreen during initialization.
     */
    fun registerSceneNarrationCallback(callback: () -> Unit) {
        sceneNarrationCallback = callback
    }

    /**
     * Request a rich scene narration describing the current camera view.
     * This is triggered when Hazel receives the wake phrase "what am I looking at".
     *
     * TODO(Naren integration): route Hazel intent
     *   BlindAssist(subAction = DESCRIBE_SCENE) here.
     */
    fun requestSceneNarration() {
        sceneNarrationCallback?.invoke()
    }
}
