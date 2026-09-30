package com.teamdexters.limitless.ui.mobility

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.routing.engine.AccessibleRouter
import com.teamdexters.limitless.routing.graph.ChennaiDemoGraph
import com.teamdexters.limitless.routing.indoor.IndoorNavScreen
import com.teamdexters.limitless.routing.indoor.StartIndoorNavButton
import com.teamdexters.limitless.routing.model.AccessibilityFilter
import com.teamdexters.limitless.routing.model.Route
import com.teamdexters.limitless.routing.model.RouteStep
import com.teamdexters.limitless.routing.model.TurnType
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.PersonaMobility
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import java.util.Locale

/**
 * Mobility & Wheelchair Home Screen (Phase 6).
 *
 * Features:
 * - Campus Destination & Origin selector for KCG campus map.
 * - 4 Accessible Filter Toggles (Ramp, Elevator, Wide Doorway, Accessible Restroom).
 * - Weighted pathfinding via [AccessibleRouter] (Dijkstra/A* algorithm).
 * - Turn-by-Turn accessible guidance with step instructions and metrics.
 * - Prominent alert banner on [HighlightBox] (#FFDBDF) for missing data or forced fallbacks.
 * - Seamless transition to Phase 5 Indoor Navigation QR/PDR mode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MobilityHomeScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    val router = remember { AccessibleRouter() }

    // -- Navigation Destinations List -----------------------------------------
    val destinationNodes = remember {
        listOf(
            "LIBRARY_2ND_FLOOR" to "Library 2nd Floor (Quiet Zone)",
            "TECH_BLOCK_LOBBY" to "Tech Block Central Lobby",
            "ADMIN_BLOCK" to "Admin Block Entrance",
            "CAFETERIA" to "Student Cafeteria",
            "RESTROOM_ACCESSIBLE" to "Ground Floor Accessible Restroom",
            "AUDITORIUM" to "Main Auditorium",
            "UNVERIFIED_SHORTCUT" to "Rear Service Pathway (Unverified)"
        )
    }

    var selectedOriginId by remember { mutableStateOf("KCG_MAIN_GATE") }
    var selectedDestId by remember { mutableStateOf("LIBRARY_2ND_FLOOR") }

    var originDropdownExpanded by remember { mutableStateOf(false) }
    var destDropdownExpanded by remember { mutableStateOf(false) }

    // -- Filter State ---------------------------------------------------------
    var requireRamp by remember { mutableStateOf(true) }
    var requireLift by remember { mutableStateOf(true) }
    var requireWideDoorway by remember { mutableStateOf(false) }
    var requireAccessibleWashroom by remember { mutableStateOf(false) }

    // -- Active Route & Inline Indoor Nav Mode ---------------------------------
    var currentRoute by remember { mutableStateOf<Route?>(null) }
    var showIndoorNavScreen by remember { mutableStateOf(false) }

    // -- TextToSpeech Shared Instance -----------------------------------------
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale.US
                ttsReady = true
            }
        }
        tts.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts.value = null
        }
    }

    fun speak(text: String) {
        if (ttsReady && text.isNotBlank()) {
            tts.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mobility_tts")
        }
    }

    var isComputing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Function to calculate route using AccessibleRouter
    fun calculateRoute() {
        if (isComputing) return
        isComputing = true
        coroutineScope.launch {
            val filter = AccessibilityFilter(
                requireRamp = requireRamp,
                requireLift = requireLift,
                requireWideDoorway = requireWideDoorway,
                requireAccessibleWashroom = requireAccessibleWashroom
            )
            val computed = withContext(Dispatchers.Default) {
                router.findRoute(
                    startNodeId = selectedOriginId,
                    destinationNodeId = selectedDestId,
                    filter = filter
                )
            }
            currentRoute = computed
            isComputing = false

            val summaryPrompt = buildString {
                append("Route found. Total distance ${computed.totalDistanceMeters} meters, ")
                append("estimated travel time ${computed.estimatedTimeSeconds / 60} minutes.")
                computed.fallbackWarning?.let { warning ->
                    append(" $warning")
                }
            }
            speak(summaryPrompt)
        }
    }

    // Auto-calculate initial route on screen load
    LaunchedEffect(selectedOriginId, selectedDestId, requireRamp, requireLift, requireWideDoorway, requireAccessibleWashroom) {
        calculateRoute()
    }

    // If user clicked "Start Indoor Nav", display full-screen IndoorNavScreen
    if (showIndoorNavScreen) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PersonaMobility)
                    .clickable { showIndoorNavScreen = false }
                    .padding(vertical = 10.dp, horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Outdoor Route",
                        tint = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "← Back to Outdoor Route Results",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
            IndoorNavScreen()
        }
        return
    }

    // Intercept physical phone back gestures & hardware back buttons
    BackHandler(enabled = true) {
        android.util.Log.e("NAV_DEBUG", "System BackHandler triggered in MobilityHomeScreen")
        onBack()
    }

    // -- Main Screen Column ----------------------------------------------------
    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .zIndex(100f) // Guarantees touches are never intercepted by overlays
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            android.util.Log.e("NAV_DEBUG", "TopBar Back Button Clicked")
                            onBack()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back to persona selection",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mobility & Navigation",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        },
        containerColor = LimitlessBackground
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = "Mobility and Wheelchair Accessible Navigation Screen" }
    ) {

        // 1. Header -----------------------------------------------------------
        Text(
            text = "Mobility & Accessible Navigation",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Mobility and Accessible Navigation header" }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .height(4.dp)
                .background(PersonaMobility, RoundedCornerShape(2.dp))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Destination & Origin Selectors -----------------------------------
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Starting Waypoint",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))

            val originName = ChennaiDemoGraph.nodes[selectedOriginId]?.name ?: selectedOriginId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceTint, RoundedCornerShape(12.dp))
                    .border(1.dp, PersonaMobility, RoundedCornerShape(12.dp))
                    .clickable { originDropdownExpanded = true }
                    .padding(14.dp)
            ) {
                Text(text = originName, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                DropdownMenu(
                    expanded = originDropdownExpanded,
                    onDismissRequest = { originDropdownExpanded = false }
                ) {
                    ChennaiDemoGraph.nodes.values.forEach { node ->
                        DropdownMenuItem(
                            text = { Text(node.name) },
                            onClick = {
                                selectedOriginId = node.id
                                originDropdownExpanded = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Destination",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))

            val destName = ChennaiDemoGraph.nodes[selectedDestId]?.name ?: selectedDestId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceTint, RoundedCornerShape(12.dp))
                    .border(1.dp, PersonaMobility, RoundedCornerShape(12.dp))
                    .clickable { destDropdownExpanded = true }
                    .padding(14.dp)
            ) {
                Text(text = destName, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                DropdownMenu(
                    expanded = destDropdownExpanded,
                    onDismissRequest = { destDropdownExpanded = false }
                ) {
                    destinationNodes.forEach { (nodeId, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedDestId = nodeId
                                destDropdownExpanded = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Accessibility Filter Toggles -------------------------------------
        Text(
            text = "Accessibility Requirements",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChipItem(
                label = "Require Ramp",
                icon = Icons.Default.Accessible,
                isSelected = requireRamp,
                onToggle = {
                    requireRamp = !requireRamp
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
            FilterChipItem(
                label = "Require Lift",
                icon = Icons.Default.Elevator,
                isSelected = requireLift,
                onToggle = {
                    requireLift = !requireLift
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
            FilterChipItem(
                label = "Wide Door (≥90cm)",
                icon = Icons.Default.MeetingRoom,
                isSelected = requireWideDoorway,
                onToggle = {
                    requireWideDoorway = !requireWideDoorway
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
            FilterChipItem(
                label = "Accessible Restroom",
                icon = Icons.Default.Wc,
                isSelected = requireAccessibleWashroom,
                onToggle = {
                    requireAccessibleWashroom = !requireAccessibleWashroom
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. "Find Accessible Route" CTA Button -------------------------------
        var isComputeFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(PersonaMobility, RoundedCornerShape(16.dp))
                .border(
                    width = if (isComputeFocused) 2.5.dp else 1.dp,
                    color = if (isComputeFocused) TextPrimary else PersonaMobility.copy(alpha = 0.40f),
                    shape = RoundedCornerShape(16.dp)
                )
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Find accessible route button"
                    onClick(label = "Find accessible route") {
                        if (!isComputing) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            calculateRoute()
                        }
                        true
                    }
                }
                .clickable(enabled = !isComputing) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    calculateRoute()
                }
                .focusable()
                .onFocusChanged { isComputeFocused = it.isFocused },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isComputing) {
                    com.teamdexters.limitless.ui.components.SafeCircularProgressIndicator(
                        color = TextPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Computing Route...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Find Accessible Route",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Route Results & Fallback Warning View ----------------------------
        currentRoute?.let { route ->
            // Summary Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceTint, RoundedCornerShape(16.dp))
                    .border(1.dp, PersonaMobility, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Distance: ${route.totalDistanceMeters} m",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Est. Time: ${route.estimatedTimeSeconds / 60} mins",
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                        }

                        // Fully Accessible Status Pill
                        val pillBg = if (route.isFullyAccessible) PersonaMobility else HighlightBox
                        val pillText = if (route.isFullyAccessible) "Fully Accessible" else "Obstacles Present"
                        Box(
                            modifier = Modifier
                                .background(pillBg, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = pillText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CRITICAL: Fallback Warning Alert Banner
            route.fallbackWarning?.let { warningMessage ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HighlightBox, RoundedCornerShape(16.dp))
                        .border(1.5.dp, TextPrimary, RoundedCornerShape(16.dp))
                        .semantics { contentDescription = "Fallback warning banner: $warningMessage" }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = warningMessage,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Turn-by-Turn Steps List
            Text(
                text = "Turn-by-Turn Guidance",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                route.steps.forEachIndexed { _, step ->
                    RouteStepItem(step = step)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Seamless Indoor Navigation Transition Button
            StartIndoorNavButton(
                onStartIndoorNav = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showIndoorNavScreen = true
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    } // end Scaffold
}

@Composable
private fun FilterChipItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = if (isSelected) PersonaMobility else SurfaceTint
    val borderWidth = if (isFocused) 2.dp else 1.dp
    val borderColor = if (isFocused) TextPrimary else PersonaMobility.copy(alpha = 0.40f)
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                contentDescription = "$label filter. ${if (isSelected) "Selected" else "Not Selected"}"
                onClick(label = "Toggle $label") {
                    onToggle()
                    true
                }
            }
            .clickable { onToggle() }
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = fontWeight,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun RouteStepItem(step: RouteStep) {
    val stepIcon = when (step.turnType) {
        TurnType.STRAIGHT -> Icons.Default.ArrowUpward
        TurnType.LEFT -> Icons.Default.ArrowBack
        TurnType.RIGHT -> Icons.Default.ArrowForward
        TurnType.SLIGHT_LEFT -> Icons.Default.ArrowBack
        TurnType.SLIGHT_RIGHT -> Icons.Default.ArrowForward
        TurnType.ARRIVE -> Icons.Default.LocationOn
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceTint, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = stepIcon,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.instruction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                step.accessibilityNotes?.let { notes ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = notes,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary.copy(alpha = 0.8f)
                    )
                }
            }

            if (step.distanceMeters > 0) {
                Box(
                    modifier = Modifier
                        .background(PersonaMobility, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${step.distanceMeters}m",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
