package com.example.nll_sensortotext

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.location.*
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class SensorData : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var gameRotationVector: Sensor? = null
    private var magnetometer: Sensor? = null
    private var pressureSensor: Sensor? = null
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private var isGpsInitialized = false
    private lateinit var csvFile: File
    private lateinit var csvWriter: FileWriter
    private var sensorExecutor: ScheduledExecutorService? = null
    private lateinit var fileNameEditText: EditText


    private val accelValues = FloatArray(3)
    private val gyroValues = FloatArray(3)
    private val orientationValues = FloatArray(3)
    private val magnetometerValues = FloatArray(3)
    private var pressureValue: Float = 0.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensordata)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        fileNameEditText = findViewById(R.id.fileNameEditText)
        val btnStopRecord: Button = findViewById(R.id.Stopbtn)

        btnStopRecord.setOnClickListener {
            val fileName = fileNameEditText.text.toString().trim()
            if (fileName.isNotEmpty()) {
                saveDataWithCustomFileName(fileName)
            }
            finish()
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gameRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        listOf(accelerometer, gyroscope, gameRotationVector, magnetometer, pressureSensor).forEach {
            it?.let { sensor -> sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST) }
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    currentLocation = location
                    if (!isGpsInitialized) {
                        isGpsInitialized = true
                        Toast.makeText(this@SensorData, "데이터 수집을 시작합니다", Toast.LENGTH_SHORT).show()
                        startLoggingTasks()
                    }
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            })
        }

        csvFile = createCsvFile()
        csvWriter = FileWriter(csvFile).apply {
            append("Time,Accel_X,Accel_Y,Accel_Z,Gyro_X,Gyro_Y,Gyro_Z,Mag_X,Mag_Y,Mag_Z,Orient_X,Orient_Y,Orient_Z,Pressure,Latitude,Longitude,Altitude,Speed\n")
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun startLoggingTasks() {
        if (sensorExecutor == null || sensorExecutor!!.isShutdown) {
            sensorExecutor = Executors.newSingleThreadScheduledExecutor()
            sensorExecutor!!.scheduleAtFixedRate({ logSensorDataSafely() }, 0, 20, TimeUnit.MILLISECONDS)
        }
    }

    private fun logSensorDataSafely() {
        if (::csvWriter.isInitialized) {
            logSensorData()
        }
    }

    private fun createCsvFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Seoul")
        }.format(Date())
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SensorData").apply {
            if (!exists()) mkdirs()
        }
        return File(directory, "sensor_data_$timeStamp.csv")
    }

    private fun logSensorData() {
        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.KOREA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Seoul")
        }.format(Date())

        val gpsData = currentLocation?.let {
            "${it.latitude},${it.longitude},${it.altitude},${it.speed}"
        } ?: " , , , "

        val sensorData = "$formattedTime,${accelValues.joinToString(",")},${gyroValues.joinToString(",")},${magnetometerValues.joinToString(",")},${orientationValues.joinToString(",")},$pressureValue,$gpsData\n"

        try {
            csvWriter.append(sensorData)
            csvWriter.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun saveDataWithCustomFileName(fileName: String) {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SensorData").apply {
            if (!exists()) mkdirs()
        }
        val customFile = File(directory, "$fileName.csv")
        csvWriter.close()
        csvFile.copyTo(customFile, overwrite = true)
        csvFile.delete()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, accelValues, 0, 3)
            Sensor.TYPE_GYROSCOPE -> System.arraycopy(event.values, 0, gyroValues, 0, 3)
            Sensor.TYPE_GAME_ROTATION_VECTOR -> System.arraycopy(event.values, 0, orientationValues, 0, 3)
            Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, magnetometerValues, 0, 3)
            Sensor.TYPE_PRESSURE -> pressureValue = event.values[0]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            sensorManager.unregisterListener(this)
            sensorExecutor?.shutdownNow() // 즉시 모든 작업 중단
            csvWriter.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
