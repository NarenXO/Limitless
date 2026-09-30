package com.teamdexters.limitless.core.hazel

sealed class HazelCommand {
    data class Navigate(val destination: String) : HazelCommand()
    object VisionAnalyze : HazelCommand()
    object StartSOS : HazelCommand()
    data class SwitchTab(val index: Int) : HazelCommand()
    data class SpeakAAC(val text: String) : HazelCommand()
    object QueryLocation : HazelCommand()
}
