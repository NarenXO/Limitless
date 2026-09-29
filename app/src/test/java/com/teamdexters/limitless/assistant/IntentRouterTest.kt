package com.teamdexters.limitless.assistant

import com.teamdexters.limitless.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DefaultIntentRouter] to verify on-device intent classification.
 */
class IntentRouterTest {

    private lateinit var router: IntentRouter

    @Before
    fun setUp() {
        router = DefaultIntentRouter()
    }

    @Test
    fun testBlindReadTextIntent() {
        val result = router.routeIntent("Hey Hazel read this document for me")
        assertEquals(HazelIntent.BlindAssist("READ_TEXT"), result)
    }

    @Test
    fun testDeafCaptionsIntent() {
        val result = router.routeIntent("Can you start live captions")
        assertEquals(HazelIntent.DeafAssist("CAPTIONS"), result)
    }

    @Test
    fun testSpeechEmergencyIntent() {
        val result = router.routeIntent("Open my emergency phrase card")
        assertEquals(HazelIntent.SpeechAssist("EMERGENCY"), result)
    }

    @Test
    fun testMobilityRoutingIntent() {
        val result = router.routeIntent("Find wheelchair accessible route to central station")
        assertEquals(HazelIntent.MobilityAssist("ROUTING"), result)
    }

    @Test
    fun testOpenScannerIntent() {
        val result = router.routeIntent("Scan this building entrance")
        assertTrue("Expected OpenScanner intent", result is HazelIntent.OpenScanner)
    }

    @Test
    fun testGeneralQueryIntent() {
        val phrase = "What is the capital of France?"
        val result = router.routeIntent(phrase)
        assertEquals(HazelIntent.GeneralQuery(phrase), result)
    }

    @Test
    fun testDetectColorIntent() {
        val result = router.routeIntent("Detect color of this object")
        assertEquals(HazelIntent.BlindAssist("DETECT_COLOR"), result)
    }

    @Test
    fun testDescribeSceneIntent() {
        val phrase = "Describe scene in front of me"
        val result = router.routeIntent(phrase)
        assertEquals(HazelIntent.VisionQuery(phrase), result)
    }

    @Test
    fun testExploreSurroundingsIntent() {
        val result = router.routeIntent("Explore surroundings")
        assertEquals(HazelIntent.BlindAssist("SURROUNDINGS"), result)
    }

    @Test
    fun testSoundAlertsIntent() {
        val result = router.routeIntent("Listen for doorbell sound alerts")
        assertEquals(HazelIntent.DeafAssist("SOUND_ALERTS"), result)
    }

    @Test
    fun testTranslateIntent() {
        val result = router.routeIntent("Translate sign language")
        assertEquals(HazelIntent.DeafAssist("TRANSLATE"), result)
    }

    @Test
    fun testTypeToSpeechIntent() {
        val result = router.routeIntent("I need type to speech")
        assertEquals(HazelIntent.SpeechAssist("TYPE_TO_SPEECH"), result)
    }

    @Test
    fun testEmotionCardsIntent() {
        val result = router.routeIntent("Show my emotion cards")
        assertEquals(HazelIntent.SpeechAssist("EMOTION_CARDS"), result)
    }

    @Test
    fun testOpenPhraseCardsIntent() {
        val result = router.routeIntent("Open phrase cards")
        assertEquals(HazelIntent.OpenPhraseCards(), result)
    }

    @Test
    fun testOpenCommunityIntent() {
        val result = router.routeIntent("Check community reports")
        assertTrue("Expected OpenCommunity intent", result is HazelIntent.OpenCommunity)
    }

    @Test
    fun testIndoorMobilityIntent() {
        val result = router.routeIntent("Find elevator or ramp nearby")
        assertEquals(HazelIntent.MobilityAssist("INDOOR"), result)
    }

    @Test
    fun testPersonaSelectIntent() {
        val result = router.routeIntent("Switch assist persona")
        assertEquals(HazelIntent.NavigateTo(Screen.PersonaSelect.route, "Persona Select"), result)
    }

    @Test
    fun testMainMenuIntent() {
        val result = router.routeIntent("Take me to main menu")
        assertEquals(HazelIntent.NavigateTo(Screen.PersonaSelect.route, "Persona Select"), result)
    }

    @Test
    fun testBlindHomeNavigation() {
        val result = router.routeIntent("Open blind assist")
        assertEquals(HazelIntent.NavigateTo(Screen.BlindHome.route, "Blind Assist"), result)
    }

    @Test
    fun testDeafHomeNavigation() {
        val result = router.routeIntent("Open deaf assist")
        assertEquals(HazelIntent.NavigateTo(Screen.DeafHome.route, "Deaf Assist"), result)
    }

    @Test
    fun testSpeechHomeNavigation() {
        val result = router.routeIntent("Open speech assist")
        assertEquals(HazelIntent.NavigateTo(Screen.SpeechHome.route, "Speech Assist"), result)
    }

    @Test
    fun testMobilityHomeNavigation() {
        val result = router.routeIntent("Open mobility assist")
        assertEquals(HazelIntent.NavigateTo(Screen.MobilityHome.route, "Mobility Assist"), result)
    }

    @Test
    fun testUnmatchedGeneralQuery() {
        val query = "Who won the World Cup in 2022?"
        val result = router.routeIntent(query)
        assertEquals(HazelIntent.GeneralQuery(query), result)
    }
}
