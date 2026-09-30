package com.teamdexters.limitless.ui.blind

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamdexters.limitless.core.hazel.HazelCommand
import com.teamdexters.limitless.hazel.HazelActionDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlindViewModel @Inject constructor(
    private val hazelActionDispatcher: HazelActionDispatcher
) : ViewModel() {

    private val _visionResult = MutableStateFlow<String?>(null)
    val visionResult: StateFlow<String?> = _visionResult.asStateFlow()

    init {
        viewModelScope.launch {
            hazelActionDispatcher.systemCommand.collect { command ->
                when (command) {
                    is HazelCommand.VisionAnalyze -> {
                        Log.d("LIMITLESS_TRACE", "[BlindViewModel] Received VisionAnalyze command")
                        // Mock vision engine processing
                        _visionResult.value = "Vision processing requested..."
                    }
                    else -> {
                        // Ignore other commands
                    }
                }
            }
        }
    }
    
    fun clearResult() {
        _visionResult.value = null
    }
}
