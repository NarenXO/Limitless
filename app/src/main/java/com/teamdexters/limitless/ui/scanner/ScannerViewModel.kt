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
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scoreDao: AccessibilityScoreDao
) : ViewModel() {

    private val analyzer = ScannerAnalyzer()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

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

    fun startScan(bitmap: Bitmap) {
        viewModelScope.launch {
            _isScanning.value = true
            
            val (objects, text, lighting) = analyzer.analyzeFrame(bitmap)
            
            _scanObjects.value = objects
            _signageText.value = text
            _lightingScore.value = lighting
            
            _isScanning.value = false
        }
    }

    fun saveScan(onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                // Calculate sub-scores based on requirements
                val objectsFound = _scanObjects.value.count {
                    it.confidence > 0.5f && (it.label.equals("ramp", ignoreCase = true) || it.label.equals("stairs", ignoreCase = true) || it.label.equals("handrail", ignoreCase = true))
                }
                val objectScore = (objectsFound * 25f).coerceIn(0f, 100f)

                val ocrScore = (_signageText.value.size * 20f).coerceIn(0f, 100f)

                val brightnessScore = _lightingScore.value.toFloat()

                val doorWidthScoreVal = when (_doorWidth.value) {
                    "Narrow" -> 0f
                    "Standard" -> 50f
                    "Wide" -> 100f
                    else -> 0f
                }
                val brailleScoreVal = if (_hasBraille.value) 100f else 0f
                val washroomScoreVal = if (_hasWashroom.value) 100f else 0f
                val checklistScore = (doorWidthScoreVal + brailleScoreVal + washroomScoreVal) / 3f

                val computedScore = ScoreCalculator.calculateCombinedScore(
                    objectScore, ocrScore, brightnessScore, checklistScore
                )
                
                // TODO(Naren): AccessibilityScoreEntity needs an isTeamVerified: Boolean field (default false)
                val isVerified = computedScore >= 70

                val entity = AccessibilityScoreEntity(
                    buildingName = "Scanned Location",
                    overallScore = computedScore,
                    rampDetected = _scanObjects.value.any { it.label.equals("ramp", ignoreCase = true) },
                    stairsDetected = _scanObjects.value.any { it.label.equals("stairs", ignoreCase = true) },
                    handrailsDetected = _scanObjects.value.any { it.label.equals("handrail", ignoreCase = true) },
                    doorWidthScore = doorWidthScoreVal / 100f,
                    lightingScore = brightnessScore / 100f,
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
