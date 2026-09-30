package com.teamdexters.limitless.ui.theme

import androidx.compose.ui.graphics.Color

// ── LIGHT MODE — Nothing OS inspired monochrome ─────────────────────────────
val PureWhite = Color(0xFFFFFFFF)       // Root background
val SoftWhite = Color(0xFFF5F5F5)       // Card background
val LightGray = Color(0xFFEEEEEE)       // Input fields, elevated surfaces
val SubtleDivider = Color(0xFFDDDDDD)   // 1px borders
val NeutralGray = Color(0xFF6B6B6B)     // Subtitles, metadata, hints
val SoftBlack = Color(0xFF1F1F1F)       // Body text
val PureBlack = Color(0xFF000000)       // Headings, titles, primary buttons, icons
val SosRed = Color(0xFFFF2B2B)          // Emergency / SOS ONLY

// ── Compatibility aliases (mapped to light palette) ─────────────────────────
val LimitlessBackground = PureWhite
val LimitlessPrimary = PureBlack
val PersonaBlind = PureBlack
val PersonaDeaf = PureBlack
val PersonaSpeech = PureBlack
val PersonaMobility = PureBlack
val HighlightBox = LightGray
val SurfaceTint = SoftWhite
val TextPrimary = PureBlack
val DarkCharcoal = SoftWhite           // Cards now use light bg
val DeepGraphite = LightGray           // Secondary surfaces
