package com.teamdexters.limitless.ui.navigation

/**
 * Sealed class defining all navigation routes in the Limitless application.
 * Each screen has a corresponding route string for navigation.
 */
sealed class Screen(val route: String) {
    /**
     * Persona selection screen where users choose their accessibility mode.
     * Route: "persona-select"
     */
    data object PersonaSelect : Screen("persona-select")

    /**
     * Home screen for blind and low-vision users.
     * Route: "blind-home"
     */
    data object BlindHome : Screen("blind-home")

    /**
     * Home screen for deaf and hard-of-hearing users.
     * Route: "deaf-home"
     */
    data object DeafHome : Screen("deaf-home")

    /**
     * Home screen for speech-impaired users.
     * Route: "speech-home"
     */
    data object SpeechHome : Screen("speech-home")

    /**
     * Speech composer screen.
     * Route: "speech-composer"
     */
    data object SpeechComposer : Screen("speech-composer")

    /**
     * Home screen for mobility and wheelchair users.
     * Route: "mobility-home"
     */
    data object MobilityHome : Screen("mobility-home")

    /**
     * Accessibility scanner screen (MediaPipe & OCR audit).
     * Route: "scanner"
     */
    data object Scanner : Screen("scanner")

    /**
     * Community reports and verified local accessibility ratings screen.
     * Route: "community"
     */
    data object Community : Screen("community")

    /**
     * Location detail screen.
     * Route: "location-detail/{locationId}"
     */
    data object LocationDetail : Screen("location-detail/{locationId}") { 
        fun createRoute(locationId: Long) = "location-detail/$locationId" 
    }
}