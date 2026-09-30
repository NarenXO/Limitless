package com.teamdexters.limitless.ui.deaf

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.LimitlessPrimary
import com.teamdexters.limitless.ui.theme.TextPrimary
import com.teamdexters.limitless.ui.theme.manropeFontFamily

/**
 * Deaf home screen with tabbed interface for sound flash cards and settings.
 */
@Composable
fun DeafHomeScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Sound Alert Flash Cards", "Emergency & TTS Settings")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = LimitlessBackground,
            contentColor = TextPrimary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        Log.d("LIMITLESS_TRACE", "DeafHomeScreen: Tab selected -> $selectedTab")
                    },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            color = TextPrimary,
                            fontFamily = manropeFontFamily
                        )
                    },
                    selectedContentColor = TextPrimary,
                    unselectedContentColor = TextPrimary
                )
            }
        }
        
        when (selectedTab) {
            0 -> DeafSoundFlashScreen()
            1 -> DeafSettingsScreen()
        }
    }
}
