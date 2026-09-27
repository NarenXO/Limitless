package com.teamdexters.limitless.ui.screens

import androidx.compose.runtime.Composable
import com.teamdexters.limitless.ui.community.CommunityReportScreen

/**
 * Community Reports screen – delegates to the full Phase 1 implementation.
 * CommunityReportViewModel is instantiated via hiltViewModel() inside CommunityReportScreen.
 */
@Composable
fun CommunityScreen(navController: androidx.navigation.NavHostController) {
    CommunityReportScreen(navController = navController)
}
