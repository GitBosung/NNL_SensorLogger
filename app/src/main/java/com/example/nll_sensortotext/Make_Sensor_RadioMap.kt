package com.example.nll_sensortotext

import android.Manifest
import android.content.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class Make_Sensor_RadioMap : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var wifiManager: WifiManager
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView

    private var isCollecting = false
    private val sensorDataRows = mutableListOf<String>()

    // Wi-Fi용 새 구조: SSID 기준
    private val wifiScanResultsByTime = mutableListOf<Pair<Long, Map<String, Int>>>()
    private val allSsidSet = mutableSetOf<String>()

    private val handler = Handler(Looper.getMainLooper())
    private var lastAccel = FloatArray(3)
    private var lastGyro = FloatArray(3)

    private val sensorIntervalMs = 20L // 50Hz
    private val wifiScanIntervalMs = 4000L // 4초마다 Wi-Fi 스캔

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
            if (success) {
                val results = wifiManager.scanResults
                saveWifiResults(results)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_make_sensor_radiomap)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        btnStart.setOnClickListener { startCollection() }
        btnStop.setOnClickListener { stopCollection() }

        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
        ActivityCompat.requestPermissions(this, permissions, 0)
    }

    private fun startCollection() {
        if (isCollecting) return
        isCollecting = true
        sensorDataRows.clear()
        wifiScanResultsByTime.clear()
        allSsidSet.clear()
        tvStatus.text = "데이터 수집 중..."

        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_FASTEST)
        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_FASTEST)

        handler.post(sensorRunnable)
        handler.post(wifiRunnable)
    }

    private fun stopCollection() {
        if (!isCollecting) return
        isCollecting = false
        sensorManager.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
        unregisterReceiverSafe(wifiScanReceiver)

        saveCsv()
        tvStatus.text = "데이터 수집 완료!"
    }

    private val sensorRunnable = object : Runnable {
        override fun run() {
            if (!isCollecting) return
            val timestamp = System.currentTimeMillis()
            val row = "$timestamp,${lastAccel.joinToString(",")},${lastGyro.joinToString(",")}"
            sensorDataRows.add(row)
            handler.postDelayed(this, sensorIntervalMs)
        }
    }

    private val wifiRunnable = object : Runnable {
        override fun run() {
            if (!isCollecting) return
            wifiManager.startScan()
            handler.postDelayed(this, wifiScanIntervalMs)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> lastAccel = it.values.clone()
                Sensor.TYPE_GYROSCOPE -> lastGyro = it.values.clone()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun saveWifiResults(results: List<ScanResult>) {
        val timestamp = System.currentTimeMillis()
        val rssiMap = mutableMapOf<String, Int>()
        for (result in results) {
            val ssid = result.SSID.ifEmpty { "<unknown>" }
            rssiMap[ssid] = result.level
            allSsidSet.add(ssid)
        }
        wifiScanResultsByTime.add(timestamp to rssiMap)
    }

    private fun saveCsv() {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        try {
            // 1. 센서 데이터 저장
            val sensorFile = File(downloads, "SensorData_$timestamp.csv")
            FileWriter(sensorFile).use { writer ->
                writer.write("Timestamp,AccX,AccY,AccZ,GyroX,GyroY,GyroZ\n")
                sensorDataRows.forEach { writer.write(it + "\n") }
            }

            // 2. Wi-Fi 데이터 저장 (행: 시간, 열: SSID)
            val wifiFile = File(downloads, "WifiData_$timestamp.csv")
            val ssidList = allSsidSet.sorted()
            FileWriter(wifiFile).use { writer ->
                // 헤더 작성: Timestamp + SSID 리스트
                writer.write("Timestamp," + ssidList.joinToString(",") + "\n")
                // 시간별 스캔 결과 작성
                for ((time, rssiMap) in wifiScanResultsByTime) {
                    val row = buildString {
                        append(time)
                        for (ssid in ssidList) {
                            append(",")
                            append(rssiMap[ssid]?.toString() ?: "-100")
                        }
                    }
                    writer.write(row + "\n")
                }
            }

            Toast.makeText(this, "CSV 저장 완료", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "CSV 저장 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unregisterReceiverSafe(receiver: BroadcastReceiver) {
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // 이미 해제된 경우 무시
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiverSafe(wifiScanReceiver)
    }
}
