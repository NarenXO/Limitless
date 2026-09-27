package com.teamdexters.limitless.ui.scanner

object ScoreCalculator {
    fun calculateCombinedScore(objectScore: Float, ocrScore: Float, brightnessScore: Float, checklistScore: Float): Int {
        val weightedObject = (objectScore * 0.30f).coerceIn(0f, 30f)
        val weightedOcr = (ocrScore * 0.20f).coerceIn(0f, 20f)
        val weightedBrightness = (brightnessScore * 0.15f).coerceIn(0f, 15f)
        val weightedChecklist = (checklistScore * 0.35f).coerceIn(0f, 35f)

        val total = weightedObject + weightedOcr + weightedBrightness + weightedChecklist
        return total.toInt().coerceIn(0, 100)
    }
}
