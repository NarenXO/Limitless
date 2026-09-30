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
                    Text(
                        text = title, 
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), 
                        color = PureBlack
                    )
                }
                Divider(color = SubtleDivider, thickness = 1.dp)
            }
        },
        bottomBar = {
            Spacer(modifier = Modifier.height(56.dp).fillMaxWidth().background(PureWhite))
        }
    ) { innerPadding ->
        // Use Alignment.TopStart so content starts immediately below the header
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PureWhite),
            contentAlignment = Alignment.TopStart 
        ) {
            content(PaddingValues(16.dp))
        }
    }
}
