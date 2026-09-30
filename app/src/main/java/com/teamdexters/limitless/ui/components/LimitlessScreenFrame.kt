package com.teamdexters.limitless.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.ui.theme.*

@Composable
fun LimitlessScreenFrame(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = PureWhite,
        topBar = {
            // MUST HAVE 80dp top padding to avoid Hazel Banner
            Column(modifier = Modifier.padding(top = 80.dp).background(PureWhite)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PureBlack)
                        }
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = PureBlack)
                }
                Divider(color = SubtleDivider, thickness = 1.dp)
            }
        },
        bottomBar = {
            // Placeholder space for the 56dp bottom nav
            Spacer(modifier = Modifier.height(56.dp).fillMaxWidth().background(PureWhite))
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(PureWhite)) {
            content(PaddingValues(16.dp))
        }
    }
}
