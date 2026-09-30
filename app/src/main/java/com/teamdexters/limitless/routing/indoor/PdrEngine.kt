package com.teamdexters.limitless.routing.indoor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin

data class PdrPosition(val x: Float, val y: Float, val heading: Float)

class PdrEngine(context: Context) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _positionFlow = MutableStateFlow(PdrPosition(0f, 0f, 0f))
    val positionFlow: StateFlow<PdrPosition> = _positionFlow.asStateFlow()

    private val _currentFloor = MutableStateFlow(0)
    val currentFloor: StateFlow<Int> = _currentFloor.asStateFlow()

    private val _lastScannedWaypoint = MutableStateFlow<IndoorWaypoint?>(null)
    val lastScannedWaypoint: StateFlow<IndoorWaypoint?> = _lastScannedWaypoint.asStateFlow()

    // Internal sensor state
    private var lastStepCount: Long = -1L
    private var lastAccStepTime: Long = 0L
    private val strideLengthMeters: Float = 1.5f

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var internalHeading = 0f
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

    fun stopSensors() {
        sensorManager.unregisterListener(this)
    }

    fun resetPosition(waypoint: IndoorWaypoint) {
        _positionFlow.value = PdrPosition(waypoint.xMeters, waypoint.yMeters, _positionFlow.value.heading)
        _currentFloor.value = waypoint.floor
        _lastScannedWaypoint.value = waypoint
    }

    fun advanceStep() {
        val current = _positionFlow.value
        val rad = Math.toRadians(current.heading.toDouble())
        val newX = current.x + (strideLengthMeters * sin(rad)).toFloat()
        val newY = current.y + (strideLengthMeters * cos(rad)).toFloat()
        _positionFlow.value = current.copy(x = newX, y = newY)
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
                        val current = _positionFlow.value
                        val rad = Math.toRadians(current.heading.toDouble())
                        val newX = current.x + (distance * sin(rad)).toFloat()
                        val newY = current.y + (distance * cos(rad)).toFloat()
                        _positionFlow.value = current.copy(x = newX, y = newY)
                    }
                }
                lastStepCount = totalSteps
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val alphaGrav = 0.8f
                gravity[0] = alphaGrav * gravity[0] + (1 - alphaGrav) * event.values[0]
                gravity[1] = alphaGrav * gravity[1] + (1 - alphaGrav) * event.values[1]
                gravity[2] = alphaGrav * gravity[2] + (1 - alphaGrav) * event.values[2]
                hasGravity = true
                updateHeading()

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
                val now = System.currentTimeMillis()
                if (magnitude > 10.5f && (now - lastAccStepTime > 280)) {
                    lastAccStepTime = now
                    advanceStep()
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                val alphaMag = 0.8f
                geomagnetic[0] = alphaMag * geomagnetic[0] + (1 - alphaMag) * event.values[0]
                geomagnetic[1] = alphaMag * geomagnetic[1] + (1 - alphaMag) * event.values[1]
                geomagnetic[2] = alphaMag * geomagnetic[2] + (1 - alphaMag) * event.values[2]
                hasGeomagnetic = true
                updateHeading()
            }
        }
    }

    private fun updateHeading() {
        if (!hasGravity || !hasGeomagnetic) return

        val now = System.currentTimeMillis()
        if (now - lastUiUpdateTime < 200) return
        lastUiUpdateTime = now

        val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuthRad = orientation[0]
            var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            if (azimuthDeg < 0f) azimuthDeg += 360f

            var diff = azimuthDeg - internalHeading
            while (diff < -180f) diff += 360f
            while (diff > 180f) diff -= 360f
            internalHeading = (internalHeading + 0.15f * diff + 360f) % 360f

            val current = _positionFlow.value
            var headingDiff = kotlin.math.abs(internalHeading - current.heading)
            if (headingDiff > 180f) headingDiff = 360f - headingDiff
            if (headingDiff > 3.0f) {
                _positionFlow.value = current.copy(heading = internalHeading)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
