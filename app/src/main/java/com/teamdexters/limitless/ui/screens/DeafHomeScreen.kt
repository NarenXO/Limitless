package com.teamdexters.limitless.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.teamdexters.limitless.ui.components.LimitlessScreenFrame
import com.teamdexters.limitless.ui.theme.NeutralGray
import com.teamdexters.limitless.ui.theme.PureBlack

@Composable
fun DeafHomeScreen(onBack: () -> Unit = {}) {
    BackHandler(enabled = true) {
        onBack()
    }

    LimitlessScreenFrame(title = "Deaf & Hard-of-Hearing", onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Deaf & Hard-of-Hearing Assistant",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = PureBlack,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Sign language and caption features coming soon.",
                style = MaterialTheme.typography.bodyMedium,
                color = NeutralGray,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}