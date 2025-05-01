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
import android.os.Environment
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

    // BSSID 기준으로 수집된 RSSI 값을 저장
    private val wifiScanResultsByTime = mutableListOf<Pair<Long, Map<String, Int>>>()
    private val allBssidSet = mutableSetOf<String>()

    private val handler = Handler(Looper.getMainLooper())
    private var lastAccel = FloatArray(3)
    private var lastGyro = FloatArray(3)

    private val sensorIntervalMs = 20L    // 50Hz
    private val wifiScanIntervalMs = 4000L // 4초마다 Wi-Fi 스캔

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
            if (success) {
                saveWifiResults(wifiManager.scanResults)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_make_sensor_radiomap)

        btnStart = findViewById(R.id.btnStart)
        btnStop  = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        wifiManager  = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        btnStart.setOnClickListener { startCollection() }
        btnStop.setOnClickListener  { stopCollection() }

        // 위치 및 Wi-Fi 권한 요청
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
        allBssidSet.clear()
        tvStatus.text = "데이터 수집 중..."

        // 센서 리스너 등록
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_FASTEST
        )
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_FASTEST
        )

        // 주기적 수집 시작
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
                Sensor.TYPE_GYROSCOPE     -> lastGyro  = it.values.clone()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun saveWifiResults(results: List<ScanResult>) {
        val timestamp = System.currentTimeMillis()
        val rssiMap = mutableMapOf<String, Int>()
        for (result in results) {
            val bssid = result.BSSID
            rssiMap[bssid] = result.level
            allBssidSet.add(bssid)
        }
        wifiScanResultsByTime.add(timestamp to rssiMap)
    }

    private fun saveCsv() {
        val sdf       = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        try {
            // 1) 센서 데이터 저장
            val sensorFile = File(downloads, "SensorData_$timestamp.csv")
            FileWriter(sensorFile).use { writer ->
                writer.write("Timestamp,AccX,AccY,AccZ,GyroX,GyroY,GyroZ\n")
                sensorDataRows.forEach { writer.write(it + "\n") }
            }

            // 2) Wi-Fi 데이터 저장 (행: 시간, 열: BSSID)
            val wifiFile  = File(downloads, "WifiData_$timestamp.csv")
            val bssidList = allBssidSet.sorted()
            FileWriter(wifiFile).use { writer ->
                writer.write("Timestamp," + bssidList.joinToString(",") + "\n")
                for ((time, rssiMap) in wifiScanResultsByTime) {
                    val row = buildString {
                        append(time)
                        for (bssid in bssidList) {
                            append(",")
                            append(rssiMap[bssid]?.toString() ?: "-100")
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
        } catch (_: IllegalArgumentException) {
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
