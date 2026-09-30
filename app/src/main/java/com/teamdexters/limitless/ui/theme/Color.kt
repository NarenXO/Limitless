package com.teamdexters.limitless.ui.theme

import androidx.compose.ui.graphics.Color

// ── LIGHT MODE — Nothing OS inspired monochrome ─────────────────────────────
val PureBlack = Color(0xFF000000)       // Primary text, icons, primary buttons
val PureWhite = Color(0xFFFFFFFF)       // App background
val SoftWhite = Color(0xFFF5F5F5)       // Secondary surfaces / card backgrounds
val LightGray = Color(0xFFEEEEEE)       // Elevated surfaces, input fields
val NeutralGray = Color(0xFF6B6B6B)     // Metadata, hints, secondary text
val SubtleDivider = Color(0xFFDDDDDD)   // 1px borders and separators
val SoftBlack = Color(0xFF1A1A1A)       // Secondary headings
val SosRed = Color(0xFFFF2B2B)          // STRICTLY for Emergency/SOS only

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
