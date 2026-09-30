package com.teamdexters.limitless.audio

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MicArbiter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val currentOwner = AtomicReference<String?>(null)

    fun requestMic(owner: String): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val isTalkBackActive = am?.isTouchExplorationEnabled == true

        if (isTalkBackActive && (owner == "WAKEWORD" || owner == "YAMNET")) {
            Log.d("LIMITLESS_TRACE", "MicArbiter: $owner denied because TalkBack is active")
            return false
        }

        var granted = false
        val current = currentOwner.get()

        if (current == null) {
            granted = currentOwner.compareAndSet(null, owner)
        } else if (owner == "RHASSPY_STT" || owner == "SOS") {
            // RHASSPY_STT and SOS can preempt WAKEWORD or YAMNET
            if (current == "WAKEWORD" || current == "YAMNET") {
                granted = currentOwner.compareAndSet(current, owner)
            }
        }

        Log.d("LIMITLESS_TRACE", "MicArbiter: $owner requested mic, granted=$granted, currentOwner=$current")
        return granted
    }

    fun releaseMic(owner: String) {
        val released = currentOwner.compareAndSet(owner, null)
        if (released) {
            Log.d("LIMITLESS_TRACE", "MicArbiter: $owner released mic")
        }
    }

    fun getCurrentOwner(): String? = currentOwner.get()
}
