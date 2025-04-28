package com.example.nll_sensortotext

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class Make_RadioMap : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var etFileName: EditText
    private lateinit var etIndex: EditText
    private lateinit var btnStart: Button
    private lateinit var btnFinish: Button
    private lateinit var tvStatus: TextView

    private val wifiResultsMap = mutableMapOf<String, ScanResult>()
    private val measurementData = mutableListOf<Measurement>()

    private val PERMISSION_REQUEST_CODE = 100
    private var scanCount = 0
    private val totalScans = 3
    private val scanHandler = Handler(Looper.getMainLooper())

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(applicationContext, "ACCESS_FINE_LOCATION 권한이 없습니다.", Toast.LENGTH_SHORT).show()
                return
            }
            val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
            if (success) {
                wifiManager.scanResults.forEach { result ->
                    val existing = wifiResultsMap[result.SSID]
                    if (existing == null || result.level > existing.level) {
                        wifiResultsMap[result.SSID] = result
                    }
                }
            } else {
                Toast.makeText(applicationContext, "Wi-Fi 스캔 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class Measurement(val index: String, val rssiMap: Map<String, Int>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_make_radiomap)

        etFileName = findViewById(R.id.etFileName)
        etIndex    = findViewById(R.id.etIndex)
        btnStart   = findViewById(R.id.btnStart)
        btnFinish  = findViewById(R.id.btnFinish)
        tvStatus   = findViewById(R.id.tvStatus)

        btnFinish.isEnabled = false
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }

        btnStart.setOnClickListener {
            val index = etIndex.text.toString().trim()
            if (index.isEmpty()) {
                Toast.makeText(this, "위치 인덱스를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            wifiResultsMap.clear()
            scanCount = 0
            btnFinish.isEnabled = false
            tvStatus.text = "측정 시작합니다... (위치: $index)"
            startWifiScanning(index)
        }

        btnFinish.setOnClickListener {
            saveCsvFile()
            finish()
        }
    }

    private fun startWifiScanning(index: String) {
        val runnable = object : Runnable {
            override fun run() {
                Log.d("Make_RadioMap", "스캔 시도 $scanCount/$totalScans")
                val okFine = ContextCompat.checkSelfPermission(
                    this@Make_RadioMap, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val okNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(
                        this@Make_RadioMap, Manifest.permission.NEARBY_WIFI_DEVICES
                    ) == PackageManager.PERMISSION_GRANTED
                } else true

                if (okFine && okNearby) {
                    try {
                        val started = wifiManager.startScan()
                        Log.d("Make_RadioMap", "startScan() -> $started")
                        if (started) {
                            scanCount++
                            tvStatus.text = "Wi-Fi 스캔 중... ($scanCount/$totalScans)"
                            if (scanCount < totalScans) {
                                scanHandler.postDelayed(this, 4000)
                            } else {
                                processScanResults(index)
                            }
                        } else {
                            Log.e("Make_RadioMap", "startScan 실패")
                        }
                    } catch (e: SecurityException) {
                        Log.e("Make_RadioMap", "SecurityException: ${e.message}")
                    }
                } else {
                    Log.e("Make_RadioMap", "권한 부족")
                }
            }
        }
        scanHandler.post(runnable)
    }

    private fun processScanResults(index: String) {
        val rssiMap = wifiResultsMap.mapValues { it.value.level }
        measurementData.add(Measurement(index, rssiMap))
        wifiResultsMap.clear()
        tvStatus.text = "측정 완료! 다음 위치를 입력해주세요."
        Toast.makeText(this, "측정이 완료되었습니다!", Toast.LENGTH_SHORT).show()
        etIndex.text.clear()
        btnFinish.isEnabled = true
    }

    private fun saveCsvFile() {
        val name = etFileName.text.toString().trim()
        if (name.isEmpty() || measurementData.isEmpty()) {
            Toast.makeText(this, "파일명 또는 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        // 모든 SSID를 모아서 정렬
        val allSsids = measurementData.flatMap { it.rssiMap.keys }.distinct().sorted()
        // 헤더 고정: x, y + SSID 목록
        val headers = listOf("x", "y") + allSsids
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val fileName = "${name}_${timestamp}_Radiomap.csv"

        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloads, fileName)
            FileWriter(file).use { writer ->
                // 헤더 쓰기
                writer.write(headers.joinToString(",") + "\n")
                // 각 위치별 RSSI값 쓰기
                measurementData.forEach { (index, rssiMap) ->
                    // index를 "x,y"로 입력했다고 가정
                    val coords = index.split(",")
                    val x = coords.getOrNull(0) ?: ""
                    val y = coords.getOrNull(1) ?: ""
                    val values = allSsids.map { ssid -> rssiMap[ssid] ?: -100 }
                    val row = listOf(x, y) + values
                    writer.write(row.joinToString(",") + "\n")
                }
            }
            Toast.makeText(this, "CSV 저장됨: $fileName", Toast.LENGTH_LONG).show()
            measurementData.clear()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "CSV 저장 실패", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(wifiScanReceiver)
    }
}
