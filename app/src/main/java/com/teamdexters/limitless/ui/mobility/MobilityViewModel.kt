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
import kotlinx.coroutines.launch

class MobilityViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LimitlessDatabase.getDatabase(application)
    private val roomDao = db.mappedRoomDao()
    private val connectionDao = db.roomConnectionDao()
    private val graphBuilder = AccessibilityGraphBuilder(roomDao, connectionDao)
    val voiceNavigator = VoiceNavigator(application)

    private val _graph = MutableStateFlow<AccessibilityGraph?>(null)
    val graph: StateFlow<AccessibilityGraph?> = _graph.asStateFlow()

    private val _activeRoute = MutableStateFlow<Route?>(null)
    val activeRoute: StateFlow<Route?> = _activeRoute.asStateFlow()

    private val _isSeeding = MutableStateFlow(true)
    val isSeeding: StateFlow<Boolean> = _isSeeding.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val seeder = DemoRoomSeeder(application, roomDao, connectionDao)
            seeder.seedIfEmpty()
            val builtGraph = graphBuilder.buildGraph()
            _graph.value = builtGraph
            _isSeeding.value = false
        }
    }

    fun calculateRoute(originId: String, destId: String, preferRamp: Boolean) {
        val currentGraph = _graph.value ?: return
        val router = AStarAccessibleRouter(currentGraph)
        val route = router.findRoute(
            fromRoomId = originId,
            toRoomId = destId,
            preferRamp = preferRamp
        )
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

    fun stopNavigation() {
        voiceNavigator.stopNavigation()
        _activeRoute.value = null
    }

    override fun onCleared() {
        super.onCleared()
        voiceNavigator.shutdown()
    }
}
