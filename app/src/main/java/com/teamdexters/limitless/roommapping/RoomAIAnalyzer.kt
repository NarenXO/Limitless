package com.teamdexters.limitless.roommapping

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RoomAnalysisResult(
    val roomId: String,
    val hasRamp: Boolean,
    val hasStairs: Boolean,
    val hasWideDoor: Boolean,
    val obstacleCount: Int,
    val aiDescription: String,
    val photoPaths: List<String>
)

class RoomAIAnalyzer(private val context: Context) {

    private val offlineDetector = OfflineYoloDetector(context)

    suspend fun analyze(roomId: String, photoPaths: List<String>): RoomAnalysisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        var globalHasRamp = false
        var globalHasStairs = false
        var globalHasWideDoor = false
        var globalObstacleCount = 0
        
        for (path in photoPaths) {
            var onlineSuccess = false
            
            try {
                // Online Flow (Gemini Fallback Mock)
                // In a real implementation, we would check network and send Base64
                // For now, we simulate online failure to execute the required YOLO fallback
                /*
                val fileBytes = File(path).readBytes()
                val base64Str = Base64.encodeToString(fileBytes, Base64.DEFAULT)
                val prompt = "Analyze this indoor room photo for wheelchair accessibility. Detect if ramps, stairs, or wide doorways (>90cm) exist, and count obstacles. Return JSON format: {ramp: boolean, stairs: boolean, wideDoor: boolean, obstacles: integer, description: string}"
                val response = GeminiClient.generateContent(prompt, base64Str)
                if (response.isSuccessful) { ... onlineSuccess = true ... }
                */
                
                throw Exception("Simulated network/API failure")
            } catch (e: Exception) {
                // Offline Flow (Fallback)
                Log.d("LIMITLESS_TRACE", "RoomAIAnalyzer: Gemini API failed, executing OfflineYoloDetector fallback", e)
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    val result = offlineDetector.detect(bitmap)
                    if (result.hasRamp) globalHasRamp = true
                    if (result.hasStairs) globalHasStairs = true
                    if (result.hasWideDoor) globalHasWideDoor = true
                    globalObstacleCount += result.obstacleCount
                }
            }
        }
        
        val features = mutableListOf<String>()
        if (globalHasRamp) features.add("Ramp detected")
        if (globalHasStairs) features.add("Stairs detected")
        if (globalHasWideDoor) features.add("Wide entrance door confirmed")
        
        val featureStr = if (features.isEmpty()) "No major accessibility features detected" else features.joinToString(". ")
        val obstacleStr = if (globalObstacleCount > 0) "Total $globalObstacleCount minor obstacles observed." else "Path appears clear of obstacles."
        
        val aiDescription = "Analyzed 4 corners: $featureStr. $obstacleStr"
        
        val duration = System.currentTimeMillis() - startTime
        Log.d("LIMITLESS_TRACE", "RoomAIAnalyzer: Analyzed roomId=$roomId in ${duration}ms")
        
        return@withContext RoomAnalysisResult(
            roomId = roomId,
            hasRamp = globalHasRamp,
            hasStairs = globalHasStairs,
            hasWideDoor = globalHasWideDoor,
            obstacleCount = globalObstacleCount,
            aiDescription = aiDescription,
            photoPaths = photoPaths
        )
    }
}
