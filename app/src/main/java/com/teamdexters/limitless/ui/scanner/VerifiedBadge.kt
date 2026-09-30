package com.teamdexters.limitless.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun VerifiedBadge(isVerified: Boolean) {
    if (!isVerified) return

    Row(
        modifier = Modifier
            .background(Color(0xFFBAD6DA), RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Team-Verified location badge" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = null,
            tint = Color(0xFF1F1F1F),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "Team-Verified",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF1F1F1F)
        )
    }
}
