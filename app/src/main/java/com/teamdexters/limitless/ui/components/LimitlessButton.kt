package com.teamdexters.limitless.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.ui.theme.*

enum class ButtonStyle {
    PRIMARY, SECONDARY, EMERGENCY
}

@Composable
fun LimitlessButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    icon: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = when (style) {
        ButtonStyle.PRIMARY -> PureWhite
        ButtonStyle.SECONDARY -> Color.Transparent
        ButtonStyle.EMERGENCY -> SosRed
    }

    val contentColor = when (style) {
        ButtonStyle.PRIMARY -> PureBlack
        ButtonStyle.SECONDARY -> PureWhite
        ButtonStyle.EMERGENCY -> PureWhite
    }

    val border = if (style == ButtonStyle.SECONDARY) {
        BorderStroke(1.dp, SubtleDivider)
    } else null

    val alpha = if (isPressed) 0.5f else 1f

    Surface(
        color = bgColor.copy(alpha = alpha),
        contentColor = contentColor.copy(alpha = alpha),
        shape = RoundedCornerShape(8.dp),
        border = border,
        modifier = modifier
            .height(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
