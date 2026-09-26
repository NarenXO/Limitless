package com.teamdexters.limitless

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.LimitlessPrimary
import com.teamdexters.limitless.ui.theme.LimitlessTheme
import com.teamdexters.limitless.ui.theme.PersonaBlind
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.PersonaMobility
import com.teamdexters.limitless.ui.theme.PersonaSpeech
import com.teamdexters.limitless.ui.theme.TextPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LimitlessTheme {
                DesignSystemVerificationScreen()
            }
        }
    }
}

@Composable
fun DesignSystemVerificationScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Heading
        Text(
            text = "Limitless Design System",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Primary Action Button
        Button(
            onClick = { },
            colors = ButtonDefaults.buttonColors(
                containerColor = LimitlessPrimary
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Primary Action")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Highlighted Text Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HighlightBox, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Sample highlighted alert box",
                color = TextPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Persona Color Swatches
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PersonaSwatch(color = PersonaBlind, label = "Blind")
            PersonaSwatch(color = PersonaDeaf, label = "Deaf")
            PersonaSwatch(color = PersonaSpeech, label = "Speech")
            PersonaSwatch(color = PersonaMobility, label = "Mobility")
        }
    }
}

@Composable
fun PersonaSwatch(color: Color, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color, RoundedCornerShape(8.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary
        )
    }
}
