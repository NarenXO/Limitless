package com.teamdexters.limitless.ui.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.teamdexters.limitless.data.local.entity.UserReportEntity
import com.teamdexters.limitless.ui.theme.TextPrimary
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun CommunityMapView(
    reports: List<UserReportEntity>,
    onMapError: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedReport by remember { mutableStateOf<UserReportEntity?>(null) }
    var mapError by remember { mutableStateOf(false) }
    
    // Setup OSMDroid Config synchronously
    remember {
        try {
            Configuration.getInstance().userAgentValue = context.packageName
        } catch (e: Throwable) {
            android.util.Log.e("LIMITLESS_CRASH", "Failed to init OSMDroid config", e)
        }
        true
    }

    if (mapError) {
        Card(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFDBDF))
        ) {
            Text(
                text = "Map offline preview unavailable. Showing location list.",
                modifier = Modifier.padding(16.dp),
                color = TextPrimary
            )
        }
        return
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Community Map View showing nearby accessibility reports" },
            factory = { ctx ->
                try {
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(14.0)
                        // Chennai Central default
                        val defaultLocation = GeoPoint(13.0827, 80.2707)
                        controller.setCenter(defaultLocation)
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("LIMITLESS_CRASH", "Failed to init MapView", e)
                    mapError = true
                    onMapError?.invoke()
                    android.view.View(ctx)
                }
            },
            update = { view ->
                if (view !is MapView) return@AndroidView
                try {
                    view.overlays.clear()
                    
                    reports.forEach { report ->
                        val marker = Marker(view)
                        // Default to Chennai if 0.0
                        val lat = if (report.latitude == 0.0) 13.0827 + (Math.random() - 0.5) * 0.05 else report.latitude
                        val lon = if (report.longitude == 0.0) 80.2707 + (Math.random() - 0.5) * 0.05 else report.longitude
                        
                        marker.position = GeoPoint(lat, lon)
                        marker.title = report.locationName
                        
                        val ratingInt = report.description.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 5
                        val markerColor = if (ratingInt >= 4) {
                            android.graphics.Color.parseColor("#BAD6DA")
                        } else {
                            android.graphics.Color.parseColor("#F791A9")
                        }
                        
                        val drawable = androidx.core.content.ContextCompat.getDrawable(view.context, android.R.drawable.ic_menu_mylocation)?.mutate()
                        drawable?.setTint(markerColor)
                        if (drawable != null) {
                            marker.icon = drawable
                        }
                        
                        marker.setOnMarkerClickListener { _, _ ->
                            selectedReport = report
                            true
                        }
                        
                        view.overlays.add(marker)
                    }
                    view.invalidate()
                } catch (e: Throwable) {
                    android.util.Log.e("LIMITLESS_CRASH", "Failed to update MapView", e)
                    mapError = true
                }
            }
        )
        
        // Selected Report Popup Card
        selectedReport?.let { report ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = report.locationName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        IconButton(onClick = { selectedReport = null }) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val rating = report.description.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 5
                    Text(
                        text = "Category: ${report.category} | Rating: $rating/5",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { /* TODO: Open details */ },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9))
                    ) {
                        Text("View Details", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
