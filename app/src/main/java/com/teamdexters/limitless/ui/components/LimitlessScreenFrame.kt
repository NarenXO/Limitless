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
import com.teamdexters.limitless.ui.theme.PureBlack
import com.teamdexters.limitless.ui.theme.PureWhite
import com.teamdexters.limitless.ui.theme.SubtleDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimitlessScreenFrame(
    title: String,
    onBack: (() -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = PureWhite,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                color = PureBlack
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            NetworkStatusBadge()
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Go back",
                                    tint = PureBlack
                                )
                            }
                        }
                    },
                    actions = {
                        if (trailingIcon != null) {
                            trailingIcon()
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
                )
                Divider(color = SubtleDivider, thickness = 1.dp)
            }
        }
        // NO dummy bottomBar Spacer — Scaffold manages inset padding natively.
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PureWhite),
            contentAlignment = Alignment.TopStart
        ) {
            content(PaddingValues(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}
