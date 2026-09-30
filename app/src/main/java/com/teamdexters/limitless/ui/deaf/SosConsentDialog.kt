package com.teamdexters.limitless.ui.deaf

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.ui.theme.manropeFontFamily

/**
 * SOS consent dialog for GDPR compliance.
 * Shows a dialog to the user before triggering SOS for the first time.
 */
@Composable
fun SosConsentDialog(
    onConsent: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFE0F2F4),
        titleContentColor = Color(0xFF1F1F1F),
        textContentColor = Color(0xFF1F1F1F),
        title = {
            Text(
                text = "Emergency SOS Consent",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F1F1F),
                fontFamily = manropeFontFamily
            )
        },
        text = {
            Text(
                text = "When activated, Limitless will:\n- Fetch your current GPS coordinates\n- Send an SMS with your Google Maps link to your emergency contacts\n- Speak an audible alert\n\nThis action cannot be undone once SMS is sent. Do you consent?",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF1F1F1F),
                fontFamily = manropeFontFamily
            )
        },
        confirmButton = {
            Button(
                onClick = onConsent,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF791A9)
                )
            ) {
                Text(
                    text = "I Consent",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F1F1F),
                    fontFamily = manropeFontFamily
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1F1F1F),
                    fontFamily = manropeFontFamily
                )
            }
        }
    )
}
