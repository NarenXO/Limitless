package com.teamdexters.limitless.ui.blind

import android.content.Context
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Full-screen navigation overlay for turn-by-turn directions.
 * Shows current step instruction, distance, and turn type indicator.
 */
@Composable
fun NavigationOverlay(
    route: List<MockRouteStep>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ttsManager = remember { TTSManager(context) }
    val vibrationHelper = remember { VibrationHelper(context) }
    
    var currentStepIndex by remember { mutableStateOf(0) }
    var isSpeaking by remember { mutableStateOf(false) }
    var shouldAutoAdvance by remember { mutableStateOf(false) }

    val currentStep = route[currentStepIndex]
    val isLastStep = currentStepIndex == route.size - 1

    // Initialize TTS
    LaunchedEffect(Unit) {
        ttsManager.initialize { success ->
            if (!success) {
                // Continue without TTS if initialization fails
            }
        }
    }

    // Speak instruction and trigger vibration when step changes
    LaunchedEffect(currentStepIndex) {
        if (isLastStep) {
            ttsManager.speak("You have arrived at your destination")
            vibrationHelper.vibrateForTurn(TurnType.ARRIVE)
        } else {
            ttsManager.speak(currentStep.instruction)
            vibrationHelper.vibrateForTurn(currentStep.turnType)
            shouldAutoAdvance = true
        }
    }

    // Auto-advance logic
    LaunchedEffect(shouldAutoAdvance) {
        if (shouldAutoAdvance && !isLastStep) {
            delay(4000) // 4 second delay
            currentStepIndex++
            shouldAutoAdvance = false
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
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
            // Header with exit button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Navigation",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceTint,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Exit")
                }
            }

            // Current step card
            NavigationStepCard(
                step = currentStep,
                stepNumber = currentStepIndex + 1,
                totalSteps = route.size,
                onStepSpoken = { isSpeaking = false }
            )

            // Navigation controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Next Step button
                Button(
                    onClick = {
                        if (!isLastStep) {
                            currentStepIndex++
                            shouldAutoAdvance = false
                        }
                    },
                    enabled = !isLastStep && !isSpeaking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .semantics {
                            contentDescription = if (isLastStep) "Arrived at destination" else "Next step"
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PersonaBlind,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isLastStep) {
                        Text(
                            text = "Arrived",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    } else {
                        Text(
                            text = "Next Step",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }

                // Step indicator
                Text(
                    text = "Step ${currentStepIndex + 1} of ${route.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }
    }
}

/**
 * Card displaying current navigation step with instruction, distance, and turn indicator.
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
        colors = CardDefaults.cardColors(
            containerColor = PersonaBlind
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Turn type indicator
            TurnTypeIndicator(
                turnType = step.turnType,
                modifier = Modifier.size(80.dp)
            )

            // Instruction
            Text(
                text = step.instruction,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 24.sp
            )

            // Distance
            if (step.distanceMeters > 0) {
                Text(
                    text = "${step.distanceMeters} meters",
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp
                )
            } else {
                Text(
                    text = "Destination",
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp
                )
            }
        }
    }
}

/**
 * Turn type indicator with flat 2D single-color arrow.
 */
@Composable
private fun TurnTypeIndicator(
    turnType: TurnType,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .drawBehind {
                val strokeWidth = 4.dp.toPx()
                val arrowColor = TextPrimary
                
                when (turnType) {
                    TurnType.LEFT -> drawLeftArrow(strokeWidth, arrowColor)
                    TurnType.RIGHT -> drawRightArrow(strokeWidth, arrowColor)
                    TurnType.STRAIGHT -> drawStraightArrow(strokeWidth, arrowColor)
                    TurnType.ARRIVE -> drawArriveIndicator(strokeWidth, arrowColor)
                }
            }
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLeftArrow(
    strokeWidth: Float,
    color: Color
) {
    val center = Offset(size.width / 2, size.height / 2)
    val arrowLength = size.minDimension * 0.4f
    
    // Draw left arrow
    drawLine(
        color = color,
        start = center,
        end = Offset(center.x - arrowLength, center.y),
        strokeWidth = strokeWidth
    )
    
    // Arrow head
    drawLine(
        color = color,
        start = Offset(center.x - arrowLength, center.y),
        end = Offset(center.x - arrowLength + 20.dp.toPx(), center.y - 20.dp.toPx()),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(center.x - arrowLength, center.y),
        end = Offset(center.x - arrowLength + 20.dp.toPx(), center.y + 20.dp.toPx()),
        strokeWidth = strokeWidth
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRightArrow(
    strokeWidth: Float,
    color: Color
) {
    val center = Offset(size.width / 2, size.height / 2)
    val arrowLength = size.minDimension * 0.4f
    
    // Draw right arrow
    drawLine(
        color = color,
        start = center,
        end = Offset(center.x + arrowLength, center.y),
        strokeWidth = strokeWidth
    )
    
    // Arrow head
    drawLine(
        color = color,
        start = Offset(center.x + arrowLength, center.y),
        end = Offset(center.x + arrowLength - 20.dp.toPx(), center.y - 20.dp.toPx()),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(center.x + arrowLength, center.y),
        end = Offset(center.x + arrowLength - 20.dp.toPx(), center.y + 20.dp.toPx()),
        strokeWidth = strokeWidth
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStraightArrow(
    strokeWidth: Float,
    color: Color
) {
    val center = Offset(size.width / 2, size.height / 2)
    val arrowLength = size.minDimension * 0.4f
    
    // Draw straight arrow (upward)
    drawLine(
        color = color,
        start = center,
        end = Offset(center.x, center.y - arrowLength),
        strokeWidth = strokeWidth
    )
    
    // Arrow head
    drawLine(
        color = color,
        start = Offset(center.x, center.y - arrowLength),
        end = Offset(center.x - 20.dp.toPx(), center.y - arrowLength + 20.dp.toPx()),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - arrowLength),
        end = Offset(center.x + 20.dp.toPx(), center.y - arrowLength + 20.dp.toPx()),
        strokeWidth = strokeWidth
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArriveIndicator(
    strokeWidth: Float,
    color: Color
) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = size.minDimension * 0.3f
    
    // Draw circle (arrive indicator)
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth)
    )
    
    // Draw checkmark
    drawLine(
        color = color,
        start = Offset(center.x - radius * 0.4f, center.y),
        end = Offset(center.x - radius * 0.1f, center.y + radius * 0.3f),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(center.x - radius * 0.1f, center.y + radius * 0.3f),
        end = Offset(center.x + radius * 0.4f, center.y - radius * 0.2f),
        strokeWidth = strokeWidth
    )
}
