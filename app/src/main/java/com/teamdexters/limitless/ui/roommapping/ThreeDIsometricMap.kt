package com.teamdexters.limitless.ui.roommapping

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.teamdexters.limitless.roommapping.SpatialRouteStep
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

// Real Satellite Tile Source (Matching Google Maps Satellite View)
val EsriSatelliteTileSource = object : OnlineTileSourceBase(
    "EsriWorldImagery",
    0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return "$baseUrl${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}"
    }
}

@Composable
fun ThreeDIsometricMap(
    steps: List<SpatialRouteStep>,
    activeStepIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Initialize osmdroid user agent safely
    LaunchedEffect(Unit) {
        try {
            Configuration.getInstance().userAgentValue = context.packageName
        } catch (e: Exception) {
            // Ignore if already set
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .border(2.dp, Color(0xFFF791A9), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(EsriSatelliteTileSource)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        controller.setZoom(18.5)

                        // Default Center: Chennai Central / Campus coordinates
                        val centerPoint = GeoPoint(13.0827, 80.2707)
                        controller.setCenter(centerPoint)
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    // Generate GeoPoints for route steps
                    val baseLat = 13.0827
                    val baseLng = 80.2707
                    val totalSteps = if (steps.isEmpty()) 3 else steps.size

                    val routePoints = mutableListOf<GeoPoint>()
                    for (i in 0 until totalSteps) {
                        val latOffset = (i * 0.00018)
                        val lngOffset = if (i % 2 == 0) (i * 0.00012) else (-i * 0.00008)
                        routePoints.add(GeoPoint(baseLat + latOffset, baseLng + lngOffset))
                    }

                    // 1. Draw Glowing Route Polyline on Satellite Imagery
                    if (routePoints.size > 1) {
                        val polyline = Polyline(mapView).apply {
                            setPoints(routePoints)
                            outlinePaint.color = android.graphics.Color.parseColor("#F791A9") // Primary Accent
                            outlinePaint.strokeWidth = 12f
                        }
                        mapView.overlays.add(polyline)
                    }

                    // 2. Add Waypoint Markers on Satellite View
                    routePoints.forEachIndexed { index, geoPoint ->
                        val marker = Marker(mapView).apply {
                            position = geoPoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = if (index == activeStepIndex) "YOU ARE HERE (Step ${index + 1})" else "Step ${index + 1}"
                            snippet = steps.getOrNull(index)?.instructionText ?: "Route Waypoint"
                        }
                        mapView.overlays.add(marker)
                    }

                    // 3. Center Satellite Camera on Active "YOU ARE HERE" Step
                    val currentPoint = routePoints.getOrElse(activeStepIndex.coerceIn(0, routePoints.size - 1)) { routePoints.first() }
                    mapView.controller.animateTo(currentPoint)
                    mapView.invalidate()
                }
            )

            // Top Status Badge: Satellite View Indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color(0xCC1F1F1F), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🛰️ SATELLITE VIEW • STEP ${activeStepIndex + 1} OF ${steps.size.coerceAtLeast(1)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom "YOU ARE HERE" Indicator Banner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(Color(0xFFF791A9), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.White, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "YOU ARE HERE (Satellite Marker)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
