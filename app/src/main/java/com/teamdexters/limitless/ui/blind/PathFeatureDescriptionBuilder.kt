package com.teamdexters.limitless.ui.blind

/**
 * Builds natural language descriptions from detected path features.
 * Similar to SceneDescriptionBuilder but specialized for accessibility path features.
 */
class PathFeatureDescriptionBuilder {

    /**
     * Build a natural language description from detected path features.
     * @param features List of detected path features
     * @return Natural language description of path features
     */
    fun buildDescription(features: List<PathFeature>): String {
        if (features.isEmpty()) {
            return "No path features detected."
        }

        val descriptions = features.map { feature ->
            "${feature.label} detected"
        }

        return when (descriptions.size) {
            1 -> descriptions[0]
            2 -> "${descriptions[0]} and ${descriptions[1]}"
            else -> {
                val allButLast = descriptions.dropLast(1).joinToString(", ")
                val last = descriptions.last()
                "$allButLast, and $last"
            }
        }
    }
}
