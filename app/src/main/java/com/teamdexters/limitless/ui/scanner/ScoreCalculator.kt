package com.teamdexters.limitless.ui.scanner

enum class PathClarityStatus(
    val title: String,
    val subtext: String,
    val badgeColorHex: Long,
    val iconName: String
) {
    CLEAR(
        title = "PATH CLEAR",
        subtext = "Safe to proceed. No hazards or major obstacles detected ahead.",
        badgeColorHex = 0xFFBAD6DA, // PersonaBlind Pastel Blue
        iconName = "CheckCircle"
    ),
    PARTIALLY_CLEAR(
        title = "PARTIALLY CLEAR",
        subtext = "Proceed with caution. Narrow doorway or minor incline detected.",
        badgeColorHex = 0xFFDDDD7B, // PersonaSpeech Yellow/Gold
        iconName = "Warning"
    ),
    BLOCKED_DANGER(
        title = "PATH BLOCKED / DANGER",
        subtext = "Hazard detected ahead (e.g. stairs without ramp or poor lighting). Seek alternate route.",
        badgeColorHex = 0xFFF791A9, // Primary Accent Red/Pink
        iconName = "Block"
    )
}

data class ComprehensiveScoreResult(
    val totalScore: Int, // 0-100
    val objectScore: Int, // 0-100 (30% weight)
    val ocrScore: Int, // 0-100 (20% weight)
    val brightnessScore: Int, // 0-100 (15% weight)
    val checklistScore: Int, // 0-100 (35% weight)
    val badgeColorHex: Long, // Color token based on range
    val aiReasoningExplanation: String,
    val pathClarity: PathClarityStatus
)

object ScoreCalculator {

    fun calculateComprehensiveScore(
        labels: List<ScanObjectResult>,
        texts: List<String>,
        doorWidth: String,
        hasBraille: Boolean,
        hasWashroom: Boolean,
        brightness: Int
    ): ComprehensiveScoreResult {
        // 1. Object Score
        var rampCount = 0
        var handrailCount = 0
        var doorwayCount = 0
        var stairsCount = 0
        var maxConfidence = 0f

        labels.forEach {
            val lbl = it.label.lowercase()
            if (lbl.contains("ramp")) rampCount++
            else if (lbl.contains("handrail") || lbl.contains("railing")) handrailCount++
            else if (lbl.contains("door") || lbl.contains("entrance")) doorwayCount++
            else if (lbl.contains("stair")) stairsCount++
            
            if (it.confidence > maxConfidence) {
                maxConfidence = it.confidence
            }
        }

        val rawObjScore = (rampCount * 35) + (handrailCount * 25) + (doorwayCount * 25) + (stairsCount * -15)
        val objScore = rawObjScore.coerceIn(0, 100)

        // 2. OCR Score
        val keywords = listOf("accessible", "entrance", "exit", "restroom", "pull", "push", "welcome", "open", "lift", "elevator", "ramp", "disabled", "information", "toilet")
        var kwCount = 0
        texts.forEach { text ->
            val words = text.lowercase().split("\\s+".toRegex())
            words.forEach { w ->
                if (keywords.any { it == w || w.contains(it) }) {
                    kwCount++
                }
            }
        }
        val rawOcrScore = kwCount * 25
        val ocrScore = rawOcrScore.coerceIn(0, 100)

        // 3. Brightness Score
        val brightScore = brightness.coerceIn(0, 100)

        // 4. Checklist Score
        val doorWidthScore = when (doorWidth) {
            "Narrow" -> 20
            "Standard" -> 70
            "Wide" -> 100
            else -> 0
        }
        val brailleScore = if (hasBraille) 100 else 0
        val washroomScore = if (hasWashroom) 100 else 0
        
        val checklistScore = (doorWidthScore + brailleScore + washroomScore) / 3

        // Total Score
        val totalScore = ((objScore * 0.30) + (ocrScore * 0.20) + (brightScore * 0.15) + (checklistScore * 0.35)).toInt().coerceIn(0, 100)

        // Color Code
        val colorHex = when {
            totalScore <= 40 -> 0xFFF791A9
            totalScore <= 70 -> 0xFFDDDD7B
            else -> 0xFFBAD6DA
        }

        val reasoning = generateReasoningText(totalScore, rampCount, stairsCount, handrailCount, brightScore, hasBraille, doorWidth, maxConfidence)

        val pathClarityStatus = when {
            totalScore <= 40 -> PathClarityStatus.BLOCKED_DANGER
            totalScore <= 70 -> PathClarityStatus.PARTIALLY_CLEAR
            else -> PathClarityStatus.CLEAR
        }

        return ComprehensiveScoreResult(
            totalScore = totalScore,
            objectScore = objScore,
            ocrScore = ocrScore,
            brightnessScore = brightScore,
            checklistScore = checklistScore,
            badgeColorHex = colorHex,
            aiReasoningExplanation = reasoning,
            pathClarity = pathClarityStatus
        )
    }

    fun generateReasoningText(
        totalScore: Int,
        rampCount: Int,
        stairsCount: Int,
        handrailCount: Int,
        brightScore: Int,
        hasBraille: Boolean,
        doorWidth: String,
        maxConfidence: Float = 0.9f
    ): String {
        val levelStr = when {
            totalScore <= 40 -> "Needs Improvement"
            totalScore <= 70 -> "Moderate Accessibility"
            else -> "Highly Accessible"
        }

        val sb = StringBuilder("This location scored $totalScore/100 ($levelStr). ")

        val confPct = (maxConfidence * 100).toInt()
        
        if (rampCount > 0 && handrailCount > 0) {
            sb.append("Ramp and handrail detected with high confidence ($confPct%). ")
        } else if (rampCount > 0) {
            sb.append("Ramp detected. ")
        } else if (stairsCount > 0 && rampCount == 0) {
            sb.append("Stairs were detected at the entrance with no visible ramp. ")
        } else if (stairsCount > 0) {
            sb.append("Stairs detected. ")
        }

        if (brightScore >= 70) {
            sb.append("Lighting is good ($brightScore/100)")
        } else if (brightScore <= 40) {
            sb.append("Lighting is dim ($brightScore/100)")
        } else {
            sb.append("Lighting is adequate ($brightScore/100)")
        }

        if (!hasBraille) {
            sb.append(", but braille signage was missing. ")
        } else {
            sb.append(", and braille signage is present. ")
        }

        if (doorWidth == "Wide") {
            sb.append("Manual door width check indicates a wide doorway (>90cm). ")
        } else if (doorWidth == "Standard") {
            sb.append("Manual door width check indicates a standard doorway (80-90cm). ")
        } else {
            sb.append("Manual door width check indicates a narrow doorway (<80cm). ")
        }

        if (totalScore <= 40) {
            sb.append("We recommend bringing an assistant or choosing an alternate entrance.")
        }

        return sb.toString().trim()
    }
    
    // Kept for backward compatibility
    fun calculateCombinedScore(
        labels: List<ScanObjectResult>,
        texts: List<String>,
        doorWidth: String,
        hasBraille: Boolean,
        hasWashroom: Boolean,
        brightness: Int
    ): Int {
        return calculateComprehensiveScore(labels, texts, doorWidth, hasBraille, hasWashroom, brightness).totalScore
    }
}
