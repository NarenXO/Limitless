package com.teamdexters.limitless.ui.mobility

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamdexters.limitless.data.local.LimitlessDatabase
import com.teamdexters.limitless.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MobilityViewModel @Inject constructor(
    application: Application,
    private val currentLocationTracker: CurrentLocationTracker
) : AndroidViewModel(application) {

    private val db = LimitlessDatabase.getDatabase(application)
    private val roomDao = db.mappedRoomDao()
    private val connectionDao = db.roomConnectionDao()
    private val graphBuilder = AccessibilityGraphBuilder(roomDao, connectionDao)
    private var _voiceNavigator: VoiceNavigator? = null
    val voiceNavigator: VoiceNavigator
        get() {
            if (_voiceNavigator == null) {
                _voiceNavigator = VoiceNavigator(getApplication())
            }
            return _voiceNavigator!!
        }

    private val _graph = MutableStateFlow<AccessibilityGraph?>(null)
    val graph: StateFlow<AccessibilityGraph?> = _graph.asStateFlow()

    private val _activeRoute = MutableStateFlow<Route?>(null)
    val activeRoute: StateFlow<Route?> = _activeRoute.asStateFlow()

    private val _isSeeding = MutableStateFlow(true)
    val isSeeding: StateFlow<Boolean> = _isSeeding.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (roomDao.getRoomCount().first() == 0) {
                    val seeder = DemoRoomSeeder(application, roomDao, connectionDao)
                    seeder.seedIfEmpty()
                    android.util.Log.d("LIMITLESS_TRACE", "MobilityViewModel: Pre-seeded 5 campus demo rooms")
                }
                val builtGraph = graphBuilder.buildGraph()
                _graph.value = builtGraph
            } catch (e: Exception) {
                android.util.Log.e("LIMITLESS_TRACE", "MobilityViewModel init error: ${e.localizedMessage}")
            } finally {
                _isSeeding.value = false
            }
        }
    }

    fun calculateAndNavigateRoute(startRoomId: String?, endRoomId: String, preferRamp: Boolean = true) {
        val actualStartRoomId = startRoomId 
            ?: currentLocationTracker.getCurrentRoomId() 
            ?: "LIMITLESS_ROOM_KCG_ENTRANCE"

        val currentGraph = _graph.value ?: return
        val router = AStarAccessibleRouter(currentGraph)
        val route = router.findRoute(
            fromRoomId = actualStartRoomId,
            toRoomId = endRoomId,
            preferRamp = preferRamp
        )
        val pathList = route?.steps?.joinToString(" -> ") { it.toRoomName } ?: "No Route Found"
        android.util.Log.d("LIMITLESS_TRACE", "Mobility -> [${actualStartRoomId}] to [${endRoomId}] -> Path: ${pathList}")

        if (route != null) {
            _activeRoute.value = route
            voiceNavigator.startNavigation(route)
        }
    }

    fun refreshGraph() {
        viewModelScope.launch(Dispatchers.IO) {
            val seeder = DemoRoomSeeder(getApplication(), roomDao, connectionDao)
            seeder.seedIfEmpty()
            val builtGraph = graphBuilder.buildGraph()
            _graph.value = builtGraph
        }
    }

    /**
     * Public hook for Rhasspy / Hazel voice commands (e.g. "Take me to Library")
     * // TODO(Naren): register startVoiceNavigation in HazelActionDispatcher
     */
    fun startVoiceNavigation(destinationName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rooms = roomDao.getAllRooms().first()
            val targetRoom = rooms.firstOrNull {
                it.roomName.contains(destinationName, ignoreCase = true) ||
                it.id.contains(destinationName, ignoreCase = true)
            }

            if (targetRoom == null) {
                android.util.Log.w("LIMITLESS_TRACE", "Mobility -> Room not found for query: $destinationName")
                // Trigger TTS feedback: "Could not find room $destinationName. Try Entrance, Library, or Canteen."
                return@launch
            }

            val startRoomId = currentLocationTracker.getCurrentRoomId() ?: "LIMITLESS_ROOM_KCG_ENTRANCE"
            calculateAndNavigateRoute(startRoomId = startRoomId, endRoomId = targetRoom.id)
        }
    }

    fun stopNavigation() {
        voiceNavigator.stopNavigation()
        _activeRoute.value = null
    }

    override fun onCleared() {
        super.onCleared()
        _voiceNavigator?.shutdown()
    }
}
