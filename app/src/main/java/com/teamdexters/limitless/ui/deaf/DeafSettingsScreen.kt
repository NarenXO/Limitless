package com.teamdexters.limitless.ui.deaf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.deaf.EmergencyContactStore
import com.teamdexters.limitless.ui.theme.manropeFontFamily

/**
 * Settings screen for configuring emergency contact and testing SOS SMS fallback.
 */
@Composable
fun DeafSettingsScreen() {
    val context = LocalContext.current
    
    var contactName by remember { mutableStateOf(EmergencyContactStore.loadContactName(context)) }
    var contactPhone by remember { mutableStateOf(EmergencyContactStore.loadContactPhone(context)) }
    var showConsentDialog by remember { mutableStateOf(false) }
    
    val triggerSos = {
        try {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$contactPhone"))
            smsIntent.putExtra("sms_body", "EMERGENCY: I need help! My location: https://maps.google.com/?q=13.0827,80.2707")
            context.startActivity(smsIntent)
            vibrate(context, 200)
        } catch (e: Exception) {
            Log.e("LIMITLESS_TRACE", "DeafSettingsScreen: Could not open SMS app", e)
            Toast.makeText(context, "Could not open SMS app", Toast.LENGTH_SHORT).show()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F1EE))
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Emergency Contact Setup",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F1F1F),
            fontFamily = manropeFontFamily
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = contactName,
            onValueChange = { contactName = it },
            label = { Text("Contact Name") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFBAD6DA),
                unfocusedBorderColor = Color(0xFFBAD6DA),
                focusedContainerColor = Color(0xFFE0F2F4),
                unfocusedContainerColor = Color(0xFFE0F2F4),
                cursorColor = Color(0xFF1F1F1F),
                focusedLabelColor = Color(0xFF1F1F1F),
                unfocusedLabelColor = Color(0xFF1F1F1F)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = manropeFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = Color(0xFF1F1F1F)
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = contactPhone,
            onValueChange = { contactPhone = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFBAD6DA),
                unfocusedBorderColor = Color(0xFFBAD6DA),
                focusedContainerColor = Color(0xFFE0F2F4),
                unfocusedContainerColor = Color(0xFFE0F2F4),
                cursorColor = Color(0xFF1F1F1F),
                focusedLabelColor = Color(0xFF1F1F1F),
                unfocusedLabelColor = Color(0xFF1F1F1F)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = manropeFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = Color(0xFF1F1F1F)
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                EmergencyContactStore.saveContact(context, contactName, contactPhone)
                Toast.makeText(context, "Contact saved!", Toast.LENGTH_SHORT).show()
                vibrate(context, 100)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF791A9)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Save Contact",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1F1F1F),
                fontFamily = manropeFontFamily
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (EmergencyContactStore.hasSosConsent(context)) {
                    triggerSos()
                } else {
                    showConsentDialog = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFBAD6DA)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Test SOS SMS",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1F1F1F),
                fontFamily = manropeFontFamily
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = {
                EmergencyContactStore.setSosConsent(context, false)
                Toast.makeText(context, "SOS consent revoked. You will be prompted before the next SOS trigger.", Toast.LENGTH_LONG).show()
                vibrate(context, 100)
                Log.d("LIMITLESS_TRACE", "DeafSettingsScreen: SOS consent revoked by user")
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color(0xFFBAD6DA)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF1F1F1F)
            )
        ) {
            Text(
                text = "Revoke SOS Consent",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1F1F1F),
                fontFamily = manropeFontFamily
            )
        }
    }
    
    if (showConsentDialog) {
        SosConsentDialog(
            onConsent = {
                EmergencyContactStore.setSosConsent(context, true)
                showConsentDialog = false
                triggerSos()
            },
            onDismiss = {
                showConsentDialog = false
            }
        )
    }
}

/**
 * Vibrate device for specified duration.
 * Uses VibratorManager for API 31+, fallback to getSystemService for older versions.
 */
private fun vibrate(context: Context, durationMs: Long) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    } catch (e: Exception) {
        Log.e("LIMITLESS_TRACE", "DeafSettingsScreen: Vibration failed", e)
    }
}
