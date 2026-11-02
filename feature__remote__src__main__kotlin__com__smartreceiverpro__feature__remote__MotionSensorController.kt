package com.smartreceiverpro.feature.remote

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.smartreceiverpro.engine.api.RemoteKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs

/**
 * Manages Gyroscope and Accelerometer sensors for Motion Remote mode (Section 8C).
 * Strictly enforces Section 20 performance rule:
 * - Unregisters hardware sensor listeners immediately when Motion mode is paused or exited
 *   to prevent battery drain and memory leaks.
 */
class MotionSensorController(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _motionKeyEvents = MutableSharedFlow<RemoteKey>(extraBufferCapacity = 4)
    val motionKeyEvents: SharedFlow<RemoteKey> = _motionKeyEvents.asSharedFlow()

    private var isListening = false
    private var sensitivityMultiplier: Float = 1.0f
    private var zeroOffsetPitch: Float = 0f
    private var zeroOffsetRoll: Float = 0f
    private var lastCalibrationSamplePitch: Float = 0f
    private var lastCalibrationSampleRoll: Float = 0f
    private var lastTriggerEpochMs: Long = 0L

    fun startListening(sensitivity: Float = 1.0f) {
        sensitivityMultiplier = sensitivity.coerceIn(0.3f, 2.5f)
        if (!isListening && gyroSensor != null) {
            sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_UI)
            isListening = true
        }
    }

    fun stopListening() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }

    fun calibrateCenter() {
        zeroOffsetPitch = lastCalibrationSamplePitch
        zeroOffsetRoll = lastCalibrationSampleRoll
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_GYROSCOPE) return
        val pitch = event.values[0]
        val roll = event.values[1]
        lastCalibrationSamplePitch = pitch
        lastCalibrationSampleRoll = roll

        val now = System.currentTimeMillis()
        if (now - lastTriggerEpochMs < 420L) return

        val threshold = 1.65f / sensitivityMultiplier
        val adjPitch = pitch - zeroOffsetPitch
        val adjRoll = roll - zeroOffsetRoll

        val triggeredKey = when {
            adjPitch < -threshold && abs(adjPitch) > abs(adjRoll) -> RemoteKey.UP
            adjPitch > threshold && abs(adjPitch) > abs(adjRoll) -> RemoteKey.DOWN
            adjRoll < -threshold && abs(adjRoll) > abs(adjPitch) -> RemoteKey.LEFT
            adjRoll > threshold && abs(adjRoll) > abs(adjPitch) -> RemoteKey.RIGHT
            else -> null
        }

        if (triggeredKey != null) {
            lastTriggerEpochMs = now
            _motionKeyEvents.tryEmit(triggeredKey)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
