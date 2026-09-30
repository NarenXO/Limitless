package com.teamdexters.limitless.ui.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamdexters.limitless.data.local.dao.AccessibilityScoreDao
import com.teamdexters.limitless.data.local.entity.AccessibilityScoreEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scoreDao: AccessibilityScoreDao
) : ViewModel() {

    private val analyzer = ScannerAnalyzer(context)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _liveDetections = MutableStateFlow<List<DetectedObject>>(emptyList())
    val liveDetections: StateFlow<List<DetectedObject>> = _liveDetections

    private val _currentDoorWidth = MutableStateFlow<DoorWidthCategory?>(null)
    val currentDoorWidth: StateFlow<DoorWidthCategory?> = _currentDoorWidth

    private val _scanObjects = MutableStateFlow<List<ScanObjectResult>>(emptyList())
    val scanObjects: StateFlow<List<ScanObjectResult>> = _scanObjects

    private val _signageText = MutableStateFlow<List<String>>(emptyList())
    val signageText: StateFlow<List<String>> = _signageText

    private val _lightingScore = MutableStateFlow(0)
    val lightingScore: StateFlow<Int> = _lightingScore

    private val _doorWidth = MutableStateFlow("Standard")
    val doorWidth: StateFlow<String> = _doorWidth

    private val _hasBraille = MutableStateFlow(false)
    val hasBraille: StateFlow<Boolean> = _hasBraille

    private val _hasWashroom = MutableStateFlow(false)
    val hasWashroom: StateFlow<Boolean> = _hasWashroom

    private val _saveResult = MutableStateFlow<Boolean?>(null)
    val saveResult: StateFlow<Boolean?> = _saveResult
    
    // Expose real-time Flow of all scores from Room DAO
    val scores: kotlinx.coroutines.flow.Flow<List<AccessibilityScoreEntity>> = scoreDao.getAllScores()
    
    private val _selectedScore = MutableStateFlow<AccessibilityScoreEntity?>(null)
    val selectedScore: StateFlow<AccessibilityScoreEntity?> = _selectedScore
    
    val comprehensiveScore: StateFlow<ComprehensiveScoreResult?> = kotlinx.coroutines.flow.combine(
        kotlinx.coroutines.flow.combine(_scanObjects, _signageText, _doorWidth, _hasBraille) { a, b, c, d ->
            listOf(a, b, c, d)
        },
        kotlinx.coroutines.flow.combine(_hasWashroom, _lightingScore, _currentDoorWidth) { e, f, g ->
            listOf(e, f, g)
        }
    ) { group1, group2 ->
        @Suppress("UNCHECKED_CAST")
        val objects = group1[0] as List<ScanObjectResult>
        @Suppress("UNCHECKED_CAST")
        val text = group1[1] as List<String>
        val manualDoorWidth = group1[2] as String
        val braille = group1[3] as Boolean
        val washroom = group2[0] as Boolean
        val lighting = group2[1] as Int
        val currentDoorWidth = group2[2] as DoorWidthCategory?
        
        val effectiveDoorWidth = currentDoorWidth?.label ?: manualDoorWidth
        ScoreCalculator.calculateComprehensiveScore(
            labels = objects,
            texts = text,
            doorWidth = effectiveDoorWidth,
            hasBraille = braille,
            hasWashroom = washroom,
            brightness = lighting
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), null)

    fun fetchScoreById(id: Long) {
        viewModelScope.launch {
            _selectedScore.value = scoreDao.getScoreById(id)
        }
    }

    private val _photoUri = MutableStateFlow<String?>(null)

    fun onDoorWidthSelected(width: String) {
        _doorWidth.value = width
    }

    fun onBrailleSelected(hasIt: Boolean) {
        _hasBraille.value = hasIt
    }

    fun onWashroomSelected(hasIt: Boolean) {
        _hasWashroom.value = hasIt
    }

    fun startScan(bitmap: Bitmap, rotationDegrees: Int) {
        viewModelScope.launch {
            _isScanning.value = true
            
            val (objects, text, lighting) = analyzer.analyzeFrame(bitmap, rotationDegrees)
            
            _scanObjects.value = objects
            _signageText.value = text
            _lightingScore.value = lighting
            
            _isScanning.value = false
        }
    }

    private var latestBitmap: Bitmap? = null
    private var latestRotation = 0

    fun updateLatestFrame(bitmap: Bitmap, rotationDegrees: Int) {
        latestBitmap = bitmap
        latestRotation = rotationDegrees
    }

    fun captureAndScan() {
        val bitmap = latestBitmap ?: return
        startScan(bitmap, latestRotation)
    }

    fun processLiveFrame(bitmap: Bitmap) {
        val detections = analyzer.detectObjectsLive(bitmap)
        _liveDetections.value = detections
        
        val doorwayDetection = detections.find { it.category == AccessibilityObjectType.DOORWAY }
        if (doorwayDetection != null) {
            _currentDoorWidth.value = DoorwayDepthEstimator.estimateDoorWidth(bitmap, doorwayDetection.boundingBox)
        } else {
            _currentDoorWidth.value = null
        }
    }

    fun saveScan(onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                // Update scanObjects with the highest confidence live detections
                val liveCats = _liveDetections.value.groupBy { it.category }
                val newScanObjects = _scanObjects.value.toMutableList()
                liveCats.forEach { (category, list) ->
                    val best = list.maxByOrNull { it.confidence }
                    if (best != null) {
                        newScanObjects.add(ScanObjectResult(best.label, best.confidence))
                    }
                }
                _scanObjects.value = newScanObjects

                val doorWidthValStr = _currentDoorWidth.value?.label ?: _doorWidth.value

                val computedScoreResult = ScoreCalculator.calculateComprehensiveScore(
                    _scanObjects.value,
                    _signageText.value,
                    doorWidthValStr,
                    _hasBraille.value,
                    _hasWashroom.value,
                    _lightingScore.value
                )
                val computedScore = computedScoreResult.totalScore
                
                // TODO(Naren): AccessibilityScoreEntity needs an isTeamVerified: Boolean field (default false)
                val isVerified = computedScore >= 70

                val doorWidthScoreVal = when (doorWidthValStr) {
                    "Narrow" -> 0f
                    "Standard" -> 50f
                    "Wide" -> 100f
                    else -> 0f
                }

                val entity = AccessibilityScoreEntity(
                    buildingName = "Scanned Location",
                    overallScore = computedScore,
                    rampDetected = _scanObjects.value.any { it.label.equals("ramp", ignoreCase = true) || it.label.contains("ramp", ignoreCase = true) },
                    stairsDetected = _scanObjects.value.any { it.label.equals("stairs", ignoreCase = true) || it.label.contains("stairs", ignoreCase = true) },
                    handrailsDetected = _scanObjects.value.any { it.label.equals("handrail", ignoreCase = true) || it.label.contains("handrail", ignoreCase = true) },
                    doorWidthScore = doorWidthScoreVal / 100f,
                    lightingScore = _lightingScore.value / 100f,
                    brailleSignagePresent = _hasBraille.value,
                    accessibleWashroomPresent = _hasWashroom.value,
                    photoUri = _photoUri.value,
                    // TODO(Naren): AccessibilityScoreEntity needs wheelchairEntranceDetected if required
                    isTeamVerified = isVerified
                )
                
                val insertedId = scoreDao.insertScore(entity)
                _saveResult.value = true
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(insertedId)
                }
            } catch (e: Exception) {
                _saveResult.value = false
            }
        }
    }
}
