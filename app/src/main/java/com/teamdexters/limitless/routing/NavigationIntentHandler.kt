package com.teamdexters.limitless.routing

import android.util.Log
import com.teamdexters.limitless.data.local.dao.MappedRoomDao

sealed class NavigationResult {
    data class Success(
        val route: Route,
        val spokenResponse: String,
        val fromRoomName: String,
        val toRoomName: String
    ) : NavigationResult()

    data class NeedsLocationAnchor(
        val spokenResponse: String = "Please scan a room QR code first to anchor your location."
    ) : NavigationResult()

    data class DestinationNotFound(
        val rawDestination: String,
        val spokenResponse: String = "Could not find a mapped room matching $rawDestination."
    ) : NavigationResult()

    data class NoAccessibleRoute(
        val fromRoomName: String,
        val toRoomName: String,
        val spokenResponse: String = "No accessible route found between $fromRoomName and $toRoomName."
    ) : NavigationResult()

    data class NotNavigationQuery(
        val query: String
    ) : NavigationResult()
}

// TODO(Naren): Register NavigationIntentHandler in HazelActionDispatcher / Rhasspy intent pipeline
class NavigationIntentHandler(
    private val graphBuilder: AccessibilityGraphBuilder,
    private val roomDao: MappedRoomDao,
    private val voiceNavigator: VoiceNavigator
) {

    fun extractDestination(query: String): String? {
        val lowerQuery = query.lowercase().trim()
        val patterns = listOf(
            Regex("move from here to (.+)"),
            Regex("tell me the route to (.+)"),
            Regex("take me to (.+)"),
            Regex("navigate to (.+)"),
            Regex("how do i get to (.+)"),
            Regex("show route to (.+)"),
            Regex("go to (.+)"),
            Regex("route to (.+)")
        )

        for (pattern in patterns) {
            val match = pattern.find(lowerQuery)
            if (match != null && match.groupValues.size > 1) {
                val dest = match.groupValues[1].trim()
                if (dest.isNotBlank()) return dest
            }
        }
        return null
    }

    suspend fun handleVoiceCommand(
        userQuery: String,
        currentRoomId: String?,
        preferRamp: Boolean = true
    ): NavigationResult {
        val destinationStr = extractDestination(userQuery)
        if (destinationStr == null) {
            return NavigationResult.NotNavigationQuery(userQuery)
        }

        if (currentRoomId.isNullOrBlank()) {
            Log.w("LIMITLESS_TRACE", "NavIntentHandler: Current room is null. Prompting QR scan.")
            return NavigationResult.NeedsLocationAnchor()
        }

        val allRooms = roomDao.getAllRooms()
        val fromRoom = allRooms.find { it.roomId == currentRoomId }
        
        if (fromRoom == null) {
            Log.w("LIMITLESS_TRACE", "NavIntentHandler: Current room ID not found in database.")
            return NavigationResult.NeedsLocationAnchor("Your current location is not recognized. Please scan a QR code.")
        }

        val destRoom = allRooms.find {
            it.roomName.contains(destinationStr, ignoreCase = true) ||
                    destinationStr.contains(it.roomName, ignoreCase = true)
        }

        if (destRoom == null) {
            return NavigationResult.DestinationNotFound(
                rawDestination = destinationStr,
                spokenResponse = "Could not find a mapped room matching $destinationStr."
            )
        }

        val graph = graphBuilder.buildGraph()
        val router = AStarAccessibleRouter(graph)
        val route = router.findRoute(fromRoomId = currentRoomId, toRoomId = destRoom.roomId, preferRamp = preferRamp)

        if (route == null) {
            return NavigationResult.NoAccessibleRoute(fromRoom.roomName, destRoom.roomName)
        }

        val spokenResponse = "Found accessible route to ${destRoom.roomName}. Distance is ${route.totalDistanceMeters.toInt()} meters. ${if (route.hasRamps) "Includes ramp access." else ""}".trimEnd()
        
        voiceNavigator.startNavigation(route)
        Log.d("LIMITLESS_TRACE", "NavIntentHandler: Successfully started navigation to ${destRoom.roomName}")

        return NavigationResult.Success(
            route = route,
            spokenResponse = spokenResponse,
            fromRoomName = fromRoom.roomName,
            toRoomName = destRoom.roomName
        )
    }
}
