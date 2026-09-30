package com.teamdexters.limitless.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.teamdexters.limitless.ui.theme.TextPrimary
import androidx.activity.compose.BackHandler
import com.teamdexters.limitless.ui.components.LimitlessScreenFrame

@Composable
fun DeafHomeScreen(onBack: () -> Unit = {}) {
    BackHandler(enabled = true) {
        onBack()
    }

    LimitlessScreenFrame(title = "Deaf & Hard-of-Hearing", onBack = onBack) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Deaf & Hard of Hearing Assistant",
                color = TextPrimary
            )
        }
    }
}