package com.teamdexters.limitless.hazel

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HazelContextProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryStore: HazelMemoryStore
) {

    suspend fun getSystemContextPrompt(currentPersona: String, currentRoute: String?): String = withContext(Dispatchers.IO) {
        val timeContext = getTimeContext()
        val batteryContext = getBatteryContext()
        val networkContext = getNetworkContext()
        val memoryContext = memoryStore.getFormattedHistoryForPrompt(limit = 3)

        val personaDisplay = currentPersona.ifBlank { "Unknown" }
        val screenDisplay = currentRoute ?: "Unknown"

        val sb = java.lang.StringBuilder()
        sb.appendLine("SYSTEM CONTEXT:")
        sb.appendLine("User Persona: [$personaDisplay]")
        sb.appendLine("Current Screen: [$screenDisplay]")
        sb.appendLine("Time & Day: [$timeContext]")
        sb.appendLine("Battery: [$batteryContext]")
        sb.appendLine("Network: [$networkContext]")
        if (memoryContext.isNotBlank()) {
            sb.appendLine("Recent History:")
            sb.appendLine(memoryContext)
        }

        sb.toString()
    }

    private fun getTimeContext(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }

        val sdf = SimpleDateFormat("EEEE, h:mm a", Locale.getDefault())
        val formattedTime = sdf.format(Date())
        return "$formattedTime ($timeOfDay)"
    }

    private fun getBatteryContext(): String {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        val batteryPct = if (level != -1 && scale != -1) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val statusText = if (isCharging) "Charging" else "Discharging"

        return if (batteryPct != -1) {
            "$batteryPct% - $statusText"
        } else {
            "Unknown"
        }
    }

    private fun getNetworkContext(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork
        if (activeNetwork != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return "Online"
            }
        }
        @Suppress("DEPRECATION")
        if (connectivityManager?.activeNetworkInfo?.isConnected == true) {
            return "Online"
        }
        return "Offline"
    }
}
