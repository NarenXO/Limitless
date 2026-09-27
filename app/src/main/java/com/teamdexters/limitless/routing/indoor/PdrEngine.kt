package com.teamdexters.limitless.routing.indoor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pedestrian Dead-Reckoning (PDR) engine combining Step Counter and Orientation Sensors
 * for relative indoor positioning.
 *
 * QR waypoint scans trigger [resetPosition] to eliminate sensor drift.
 */
class PdrEngine(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    // Position & Orientation State (Observable by Compose)
    var xMeters by mutableFloatStateOf(0f)
        private set
    var yMeters by mutableFloatStateOf(0f)
        private set
    var currentFloor by mutableIntStateOf(0)
        private set
    var headingDegrees by mutableFloatStateOf(0f)
        private set
    var lastScannedWaypoint by mutableStateOf<IndoorWaypoint?>(null)
        private set

    // Internal sensor state
    private var lastStepCount: Long = -1L
    private var lastAccStepTime: Long = 0L
    private val strideLengthMeters: Float = 0.75f

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var lastUiUpdateTime = 0L

    fun start() {
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        stepDetectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Hard-resets estimated position to a verified QR code waypoint location (Drift Correction).
     */
    fun resetPosition(waypoint: IndoorWaypoint) {
        xMeters = waypoint.xMeters
        yMeters = waypoint.yMeters
        currentFloor = waypoint.floor
        lastScannedWaypoint = waypoint
    }

    /**
     * Simulates step advance (used for testing or manual step triggers).
     */
    fun advanceStep() {
        val rad = Math.toRadians(headingDegrees.toDouble())
        xMeters += (strideLengthMeters * sin(rad)).toFloat()
        yMeters += (strideLengthMeters * cos(rad)).toFloat()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                advanceStep()
            }

            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values[0].toLong()
                if (lastStepCount >= 0L) {
                    val deltaSteps = (totalSteps - lastStepCount).coerceAtLeast(0L)
                    if (deltaSteps > 0) {
                        val distance = deltaSteps * strideLengthMeters
                        val rad = Math.toRadians(headingDegrees.toDouble())
                        xMeters += (distance * sin(rad)).toFloat()
                        yMeters += (distance * cos(rad)).toFloat()
                    }
                }
                lastStepCount = totalSteps
            }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
                updateHeading()

                // Accelerometer peak detection step counting fallback
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
                val now = System.currentTimeMillis()
                if (magnitude > 12.0f && (now - lastAccStepTime > 350)) {
                    lastAccStepTime = now
                    advanceStep()
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasGeomagnetic = true
                updateHeading()
            }
        }
    }

    private fun updateHeading() {
        if (!hasGravity || !hasGeomagnetic) return

        val now = System.currentTimeMillis()
        if (now - lastUiUpdateTime < 100) return // Throttle UI updates to max 10 Hz
        lastUiUpdateTime = now

        val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuthRad = orientation[0]
            var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            if (azimuthDeg < 0f) azimuthDeg += 360f

            // Low-pass filter smoothing (alpha = 0.15f)
            val alpha = 0.15f
            headingDegrees = headingDegrees + alpha * (azimuthDeg - headingDegrees)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
