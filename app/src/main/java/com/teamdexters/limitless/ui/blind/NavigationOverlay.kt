package com.teamdexters.limitless.ui.blind

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen navigation overlay for turn-by-turn directions.
 *
 * Shows:
 * - Current navigation instruction
 * - Distance to the next step
 * - Turn type indicator
 * - Voice instructions synchronized with TTS completion
 * - Vibration feedback
 * - Automatic step advancement with TTS synchronization
 */
@Composable
fun NavigationOverlay(
    route: List<MockRouteStep>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val ttsManager = remember {
        TTSManager(context)
    }

    val vibrationHelper = remember {
        VibrationHelper(context)
    }

    var currentStepIndex by remember {
        mutableStateOf(0)
    }

    var isSpeaking by remember {
        mutableStateOf(false)
    }

    var shouldAutoAdvance by remember {
        mutableStateOf(false)
    }

    var autoAdvanceJob by remember {
        mutableStateOf<kotlinx.coroutines.Job?>(null)
    }

    // Prevent an empty route from causing an IndexOutOfBoundsException.
    if (route.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(LimitlessBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "No navigation route available",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PersonaBlind,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Exit Navigation")
                }
            }
        }

        return
    }

    val currentStep = route[currentStepIndex]

    val isLastStep =
        currentStepIndex == route.size - 1

    /**
     * Initialize Text-to-Speech.
     */
    LaunchedEffect(Unit) {
        ttsManager.initialize { success ->
            if (!success) {
                // Navigation continues even if TTS initialization fails.
            }
        }
    }

    /**
     * Speak the current instruction and trigger
     * the appropriate vibration pattern whenever
     * the navigation step changes.
     * Synchronizes auto-advancement with TTS completion.
     */
    LaunchedEffect(currentStepIndex) {

        // Cancel any existing auto-advance job
        autoAdvanceJob?.cancel()

        if (isLastStep) {

            isSpeaking = true

            // Speak arrival message
            ttsManager.speak("You have arrived at your destination.") {
                isSpeaking = false
            }

            // Trigger ARRIVE vibration (600ms pulse)
            vibrationHelper.vibrateForTurn(TurnType.ARRIVE)

            // Permanently disable auto-advance on arrival
            shouldAutoAdvance = false

        } else {

            isSpeaking = true

            // Trigger appropriate vibration pattern
            vibrationHelper.vibrateForTurn(currentStep.turnType)

            // Speak current instruction with completion callback
            ttsManager.speak(currentStep.instruction) {
                // TTS finished, mark as not speaking
                isSpeaking = false
            }

            // Enable auto-advance with TTS synchronization
            shouldAutoAdvance = true
            
            // Auto-advance logic: wait for TTS completion (max 4s), then pause 1.5s, then advance
            autoAdvanceJob = launch {
                // Wait for TTS to complete (up to 4 seconds max)
                var waited = 0L
                while (isSpeaking && waited < 4000) {
                    delay(100)
                    waited += 100
                }
                
                // After TTS completes (or timeout), pause 1.5 seconds
                delay(1500)
                
                // If auto-advance is still enabled, advance to next step
                if (shouldAutoAdvance && !isLastStep) {
                    shouldAutoAdvance = false
                    currentStepIndex++
                }
            }
        }
    }

    /**
     * Clean up Text-to-Speech and cancel auto-advance when the navigation
     * overlay is removed.
     */
    DisposableEffect(Unit) {

        onDispose {
            autoAdvanceJob?.cancel()
            ttsManager.stop()
            vibrationHelper.stop()
            ttsManager.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LimitlessBackground)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),

            horizontalAlignment = Alignment.CenterHorizontally,

            verticalArrangement = Arrangement.SpaceBetween
        ) {

            /**
             * Header
             */
            Row(
                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = "Navigation",

                    style =
                        MaterialTheme.typography.headlineMedium,

                    color = TextPrimary,

                    fontWeight =
                        FontWeight.Bold
                )

                Button(
                    onClick = {
                        // Clean exit: cancel auto-advance, stop TTS, stop vibration
                        autoAdvanceJob?.cancel()
                        ttsManager.stop()
                        vibrationHelper.stop()
                        onExit()
                    },

                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = SurfaceTint,
                            contentColor = TextPrimary
                        ),

                    shape =
                        RoundedCornerShape(12.dp),

                    modifier = Modifier
                        .height(56.dp)
                        .semantics {
                            contentDescription =
                                "Exit navigation. Double tap to close."
                        }
                ) {
                    Text("Exit")
                }
            }

            /**
             * Current navigation step.
             */
            NavigationStepCard(
                step = currentStep,

                stepNumber =
                    currentStepIndex + 1,

                totalSteps =
                    route.size,

                onStepSpoken = {
                    isSpeaking = false
                }
            )

            /**
             * Navigation controls.
             */
            Column(
                modifier = Modifier.fillMaxWidth(),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.spacedBy(16.dp)
            ) {

                /**
                 * Next Step button.
                 * When manually tapped, cancels auto-advance timer, speaks new step, and restarts timing loop.
                 * On arrival, button is permanently disabled and labeled "Arrived".
                 */
                Button(
                    onClick = {

                        if (!isLastStep) {
                            // Cancel auto-advance timer
                            autoAdvanceJob?.cancel()
                            shouldAutoAdvance = false
                            
                            // Advance to next step
                            currentStepIndex++
                            
                            // LaunchedEffect will handle speaking the new step and restarting timing
                        }
                    },

                    enabled =
                        !isLastStep,

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .semantics {

                            contentDescription =
                                if (isLastStep) {
                                    "Arrived at destination"
                                } else {
                                    "Next navigation step"
                                }
                        },

                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = PersonaBlind,
                            contentColor = TextPrimary
                        ),

                    shape =
                        RoundedCornerShape(16.dp)
                ) {

                    if (isLastStep) {

                        Text(
                            text = "Arrived",

                            style =
                                MaterialTheme.typography.titleLarge,

                            fontWeight =
                                FontWeight.Bold,

                            fontSize = 20.sp
                        )

                    } else {

                        Text(
                            text = "Next Step",

                            style =
                                MaterialTheme.typography.titleLarge,

                            fontWeight =
                                FontWeight.Bold,

                            fontSize = 20.sp
                        )
                    }
                }

                /**
                 * Step counter.
                 */
                Text(
                    text =
                        "Step ${currentStepIndex + 1} of ${route.size}",

                    style =
                        MaterialTheme.typography.bodyMedium,

                    color = TextPrimary
                )
            }
        }
    }
}


/**
 * Card displaying the current navigation step.
 *
 * Shows:
 * - Turn type
 * - Navigation instruction
 * - Distance
 */
@Composable
private fun NavigationStepCard(
    step: MockRouteStep,
    stepNumber: Int,
    totalSteps: Int,
    onStepSpoken: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),

        colors =
            CardDefaults.cardColors(
                containerColor = PersonaBlind
            ),

        shape =
            RoundedCornerShape(20.dp)
    ) {

        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            /**
             * Turn type indicator.
             */
            TurnTypeIndicator(
                turnType = step.turnType,

                modifier =
                    Modifier.size(80.dp)
            )

            /**
             * Navigation instruction.
             */
            Text(
                text = step.instruction,

                style =
                    MaterialTheme.typography.headlineMedium,

                color = TextPrimary,

                fontWeight =
                    FontWeight.Bold,

                textAlign =
                    TextAlign.Center,

                fontSize = 24.sp
            )

            /**
             * Distance.
             */
            if (step.distanceMeters > 0) {

                Text(
                    text =
                        "${step.distanceMeters} meters",

                    style =
                        MaterialTheme.typography.displaySmall,

                    color = TextPrimary,

                    fontWeight =
                    FontWeight.Bold,

                    fontSize = 48.sp
                )

            } else {

                Text(
                    text = "Destination",

                    style =
                        MaterialTheme.typography.displaySmall,

                    color = TextPrimary,

                    fontWeight =
                    FontWeight.Bold,

                    fontSize = 48.sp
                )
            }

            /**
             * Step counter.
             */
            Text(
                text =
                    "Step $stepNumber of $totalSteps",

                style =
                    MaterialTheme.typography.bodyMedium,

                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,

                color = TextPrimary
            )
        }
    }
}


/**
 * Turn type indicator with a flat 2D arrow.
 */
@Composable
private fun TurnTypeIndicator(
    turnType: TurnType,
    modifier: Modifier = Modifier
) {

    Box(
        modifier = modifier.drawBehind {

            val strokeWidth =
                4.dp.toPx()

            val arrowColor =
                TextPrimary

            when (turnType) {

                TurnType.LEFT ->
                    drawLeftArrow(
                        strokeWidth,
                        arrowColor
                    )

                TurnType.RIGHT ->
                    drawRightArrow(
                        strokeWidth,
                        arrowColor
                    )

                TurnType.STRAIGHT ->
                    drawStraightArrow(
                        strokeWidth,
                        arrowColor
                    )

                TurnType.ARRIVE ->
                    drawArriveIndicator(
                        strokeWidth,
                        arrowColor
                    )
            }
        }
    )
}


/**
 * Draw left arrow.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLeftArrow(
    strokeWidth: Float,
    color: Color
) {

    val center =
        Offset(
            size.width / 2,
            size.height / 2
        )

    val arrowLength =
        size.minDimension * 0.4f

    /**
     * Main arrow line.
     */
    drawLine(
        color = color,

        start = center,

        end = Offset(
            center.x - arrowLength,
            center.y
        ),

        strokeWidth = strokeWidth
    )

    /**
     * Upper arrow head.
     */
    drawLine(
        color = color,

        start = Offset(
            center.x - arrowLength,
            center.y
        ),

        end = Offset(
            center.x -
                    arrowLength +
                    20.dp.toPx(),

            center.y -
                    20.dp.toPx()
        ),

        strokeWidth = strokeWidth
    )

    /**
     * Lower arrow head.
     */
    drawLine(
        color = color,

        start = Offset(
            center.x - arrowLength,
            center.y
        ),

        end = Offset(
            center.x -
                    arrowLength +
                    20.dp.toPx(),

            center.y +
                    20.dp.toPx()
        ),

        strokeWidth = strokeWidth
    )
}


/**
 * Draw right arrow.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRightArrow(
    strokeWidth: Float,
    color: Color
) {

    val center =
        Offset(
            size.width / 2,
            size.height / 2
        )

    val arrowLength =
        size.minDimension * 0.4f

    /**
     * Main arrow line.
     */
    drawLine(
        color = color,

        start = center,

        end = Offset(
            center.x + arrowLength,
            center.y
        ),

        strokeWidth = strokeWidth
    )

    /**
     * Upper arrow head.
     */
    drawLine(
        color = color,

        start = Offset(
            center.x + arrowLength,
            center.y
        ),

        end = Offset(
            center.x +
                    arrowLength -
                    20.dp.toPx(),

            center.y -
                    20.dp.toPx()
        ),

        strokeWidth = strokeWidth
    )

    /**
     * Lower arrow head.
     */
    drawLine(
        color = color,

        start = Offset(
            center.x + arrowLength,
            center.y
        ),

        end = Offset(
            center.x +
                    arrowLength -
                    20.dp.toPx(),

            center.y +
                    20.dp.toPx()
        ),

        strokeWidth = strokeWidth
    )
}


/**
 * Draw straight/up arrow.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStraightArrow(
    strokeWidth: Float,
    color: Color
) {

    val center =
        Offset(
            size.width / 2,
            size.height / 2
        )

    val arrowLength =
        size.minDimension * 0.4f

    /**
     * Main arrow line.
     */
    drawLine(
        color = color,

        start = center,

        end = Offset(
            center.x,
            center.y - arrowLength
        ),

        strokeWidth = strokeWidth
    )

    /**
     * Left arrow head.
     */
    drawLine(
        color = color,

        start = Offset(
            center.x,
            center.y - arrowLength
        ),

        end = Offset(
            center.x -
                    20.dp.toPx(),

            center.y -
                    arrowLength +
                    20.dp.toPx()
        ),

        strokeWidth = strokeWidth
    )

    /**
     * Right arrow head.
     */
    drawLine(
        color = color,

        start = Offset(
            center.x,
            center.y - arrowLength
        ),

        end = Offset(
            center.x +
                    20.dp.toPx(),

            center.y -
                    arrowLength +
                    20.dp.toPx()
        ),

        strokeWidth = strokeWidth
    )
}


/**
 * Draw arrival indicator.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArriveIndicator(
    strokeWidth: Float,
    color: Color
) {

    val center =
        Offset(
            size.width / 2,
            size.height / 2
        )

    val radius =
        size.minDimension * 0.3f

    /**
     * Circle.
     */
    drawCircle(
        color = color,

        radius = radius,

        center = center,

        style =
            Stroke(
                width = strokeWidth
            )
    )

    /**
     * Checkmark - first line.
     */
    drawLine(
        color = color,

        start = Offset(
            center.x -
                    radius * 0.4f,

            center.y
        ),

        end = Offset(
            center.x -
                    radius * 0.1f,

            center.y +
                    radius * 0.3f
        ),

        strokeWidth = strokeWidth
    )

    /**
     * Checkmark - second line.
     */
    drawLine(
        color = color,

        start = Offset(
            center.x -
                    radius * 0.1f,

            center.y +
                    radius * 0.3f
        ),

        end = Offset(
            center.x +
                    radius * 0.4f,

            center.y -
                    radius * 0.2f
        ),

        strokeWidth = strokeWidth
    )
}