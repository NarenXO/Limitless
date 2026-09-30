package com.teamdexters.limitless.ui.scanner

object ScoreCalculator {
    fun calculateObjectScore(labels: List<ScanObjectResult>): Float {
        var score = 0f
        if (labels.isNotEmpty()) score += 5f // Baseline

        val doorTerms = listOf("door", "building", "entrance", "house", "room", "wall", "architecture", "property")
        val floorTerms = listOf("stairs", "steps", "escalator", "floor", "tile", "pavement", "road", "sidewalk")
        val seatingTerms = listOf("chair", "wheelchair", "furniture", "seat", "table", "bench")

        if (labels.any { doorTerms.contains(it.label.lowercase()) }) score += 10f
        if (labels.any { floorTerms.contains(it.label.lowercase()) }) score += 10f
        if (labels.any { seatingTerms.contains(it.label.lowercase()) }) score += 10f

        return score.coerceIn(0f, 30f)
    }

    fun calculateOcrScore(texts: List<String>): Float {
        var score = 0f
        if (texts.isNotEmpty()) score += 10f

        val keywords = listOf("accessible", "entrance", "exit", "restroom", "pull", "push", "welcome", "open", "lift", "elevator", "ramp", "disabled", "information", "toilet")
        val foundExtra = texts.any { text -> keywords.any { kw -> text.lowercase().contains(kw) } }
        if (foundExtra) score += 10f

        return score.coerceIn(0f, 20f)
    }

    fun calculateChecklistScore(doorWidth: String, hasBraille: Boolean, hasWashroom: Boolean): Float {
        var score = 0f
        if (doorWidth == "Standard") score += 10f
        if (doorWidth == "Wide") score += 15f
        if (hasBraille) score += 10f
        if (hasWashroom) score += 10f
        return score.coerceIn(0f, 35f)
    }

    fun calculateCombinedScore(
        labels: List<ScanObjectResult>,
        texts: List<String>,
        doorWidth: String,
        hasBraille: Boolean,
        hasWashroom: Boolean,
        brightness: Int
    ): Int {
        val objScore = calculateObjectScore(labels)
        val ocrScore = calculateOcrScore(texts)
        val checkScore = calculateChecklistScore(doorWidth, hasBraille, hasWashroom)
        val lightScore = ((brightness / 100f) * 15f).coerceIn(0f, 15f) // Map 0-100 to 0-15

        val total = objScore + ocrScore + checkScore + lightScore
        return total.toInt().coerceIn(35, 100)
    }
}
