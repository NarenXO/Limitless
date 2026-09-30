package com.teamdexters.limitless.core.safety

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LimitlessSmsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val mappedRoomDao: MappedRoomDao
) {
    suspend fun sendEmergencySOS(userName: String, emergencyContact: String = "911") {
        try {
            var lat = 0.0
            var lng = 0.0
            
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                val location = suspendCancellableCoroutine<android.location.Location?> { continuation ->
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                        .addOnSuccessListener { loc -> continuation.resume(loc) }
                        .addOnFailureListener { continuation.resume(null) }
                        .addOnCanceledListener { continuation.resume(null) }
                }

                if (location != null) {
                    lat = location.latitude
                    lng = location.longitude
                }
            }

            var roomName = "Hackathon Demo Lab"
            val rooms = withContext(Dispatchers.IO) { mappedRoomDao.getAllMappedRooms().first() }
            if (rooms.isNotEmpty()) {
                roomName = rooms.first().name
            }

            val message = "EMERGENCY: $userName needs help. Location: $roomName (https://www.google.com/maps/search/?api=1&query=$lat,$lng)"
            Log.d("LIMITLESS_TRACE", "SOS Triggered -> GPS: [$lat, $lng] -> Nearest Room: [$roomName] -> Status: [Preparing]")

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(emergencyContact, null, message, null, null)
                Log.d("LIMITLESS_TRACE", "SOS Triggered -> GPS: [$lat, $lng] -> Nearest Room: [$roomName] -> Status: [Sent]")
            } else {
                val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$emergencyContact")
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(sendIntent)
                Log.d("LIMITLESS_TRACE", "SOS Triggered -> GPS: [$lat, $lng] -> Nearest Room: [$roomName] -> Status: [Sent via Intent]")
            }

        } catch (e: Exception) {
            Log.e("LIMITLESS_TRACE", "Error in SOS Manager", e)
        }
    }
}
