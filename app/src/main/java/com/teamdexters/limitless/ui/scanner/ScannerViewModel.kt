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

    fun saveScan() {
        viewModelScope.launch {
            try {
                // Calculate an overall score based on our parameters
                var computedScore = 50
                if (_hasBraille.value) computedScore += 10
                if (_hasWashroom.value) computedScore += 15
                if (_scanObjects.value.any { it.label.equals("ramp", ignoreCase = true) }) computedScore += 15
                if (_scanObjects.value.any { it.label.equals("elevator", ignoreCase = true) }) computedScore += 10
                computedScore = computedScore.coerceIn(0, 100)

                val entity = AccessibilityScoreEntity(
                    buildingName = "Scanned Location",
                    overallScore = computedScore,
                    rampDetected = _scanObjects.value.any { it.label.equals("ramp", ignoreCase = true) },
                    stairsDetected = _scanObjects.value.any { it.label.equals("stairs", ignoreCase = true) },
                    handrailsDetected = _scanObjects.value.any { it.label.equals("handrail", ignoreCase = true) },
                    doorWidthScore = when (_doorWidth.value) {
                        "Wide" -> 1.0f
                        "Standard" -> 0.7f
                        else -> 0.4f
                    },
                    lightingScore = _lightingScore.value / 100f,
                    brailleSignagePresent = _hasBraille.value,
                    accessibleWashroomPresent = _hasWashroom.value,
                    photoUri = _photoUri.value
                    // TODO(Naren): AccessibilityScoreEntity needs wheelchairEntranceDetected if required
                )
                
                scoreDao.insertScore(entity)
                _saveResult.value = true
            } catch (e: Exception) {
                _saveResult.value = false
            }
        }
    }
}
