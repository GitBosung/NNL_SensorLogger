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

    // 최초 측정 시 동적으로 선정된 10개 SSID를 저장하는 변수 (처음 측정 후 이후 측정에 동일하게 사용)
    private var dynamicAllowedWifiSSIDs: List<String>? = null

    // 키를 SSID로 하여 AP들에 대한 최상의 ScanResult를 저장합니다.
    private val wifiResultsMap: MutableMap<String, ScanResult> = mutableMapOf()
    private val measurementRows = mutableListOf<String>()
    private val PERMISSION_REQUEST_CODE = 100
    private var scanCount = 0
    private val totalScans = 3
    private val scanHandler = Handler(Looper.getMainLooper())

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(applicationContext, "ACCESS_FINE_LOCATION 권한이 없습니다.", Toast.LENGTH_SHORT).show()
                return
            }
            val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
            if (success) {
                val results = wifiManager.scanResults
                for (result in results) {
                    // 최초 측정 시 dynamicAllowedWifiSSIDs가 설정되지 않은 경우에는 모든 결과를 수집하고,
                    // 이후 측정부터는 선택된 SSID만 수집합니다.
                    if (dynamicAllowedWifiSSIDs == null || result.SSID in dynamicAllowedWifiSSIDs!!) {
                        val existing = wifiResultsMap[result.SSID]
                        if (existing == null || result.level > existing.level) {
                            wifiResultsMap[result.SSID] = result
                        }
                    }
                }
            } else {
                Toast.makeText(applicationContext, "Wi-Fi 스캔 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_make_radiomap)

        etFileName = findViewById(R.id.etFileName)
        etIndex = findViewById(R.id.etIndex)
        btnStart = findViewById(R.id.btnStart)
        btnFinish = findViewById(R.id.btnFinish)
        tvStatus = findViewById(R.id.tvStatus)

        btnFinish.isEnabled = false
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }

        btnStart.setOnClickListener {
            val index = etIndex.text.toString()
            if (index.isEmpty()) {
                Toast.makeText(this, "위치 인덱스를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 새로운 측정을 위해 결과 맵 초기화
            wifiResultsMap.clear()
            tvStatus.text = "측정 시작합니다... (위치: $index)"
            Toast.makeText(this, "측정 시작합니다", Toast.LENGTH_SHORT).show()
            btnFinish.isEnabled = false
            scanCount = 0
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
                Log.d("Make_RadioMap", "startWifiScanning: 스캔 시도 $scanCount/$totalScans")

                val hasFineLocation = ContextCompat.checkSelfPermission(
                    this@Make_RadioMap, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                val hasNearbyWifiPermission =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ContextCompat.checkSelfPermission(
                            this@Make_RadioMap, Manifest.permission.NEARBY_WIFI_DEVICES
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true

                if (hasFineLocation && hasNearbyWifiPermission) {
                    try {
                        val success = wifiManager.startScan()
                        Log.d("Make_RadioMap", "startScan() 호출 결과: $success")
                        if (success) {
                            scanCount++
                            tvStatus.text = "Wi-Fi 스캔 중... ($scanCount/$totalScans)"
                            if (scanCount < totalScans) {
                                scanHandler.postDelayed(this, 4000)
                            } else {
                                processScanResults(index)
                            }
                        } else {
                            Log.e("Make_RadioMap", "startScan() 호출 실패")
                        }
                    } catch (e: SecurityException) {
                        Log.e("Make_RadioMap", "SecurityException 발생: ${e.message}")
                    }
                } else {
                    val missing = mutableListOf<String>()
                    if (!hasFineLocation) missing.add("ACCESS_FINE_LOCATION")
                    if (!hasNearbyWifiPermission) missing.add("NEARBY_WIFI_DEVICES")
                    Log.e("Make_RadioMap", "권한 부족: ${missing.joinToString()}")
                }
            }
        }
        scanHandler.post(runnable)
    }

    private fun processScanResults(index: String) {
        // 최초 측정 시 dynamicAllowedWifiSSIDs가 아직 설정되지 않았다면,
        // wifiResultsMap에서 신호 강도 순으로 정렬하여 상위 10개의 SSID를 선택합니다.
        if (dynamicAllowedWifiSSIDs == null) {
            dynamicAllowedWifiSSIDs = wifiResultsMap.values
                .sortedByDescending { it.level }
                .map { it.SSID }
                .distinct()
                .take(10)
            Log.d("Make_RadioMap", "선택된 SSID: $dynamicAllowedWifiSSIDs")
        }
        val csvRow = StringBuilder()
        csvRow.append(index)
        // 선택된 SSID 순서대로, 해당 값이 있으면 소수점 3자리로 포맷, 없으면 -100.000 기록
        dynamicAllowedWifiSSIDs?.forEach { ssid ->
            val level: Double = if (wifiResultsMap.containsKey(ssid)) {
                wifiResultsMap[ssid]?.level?.toDouble() ?: -100.0
            } else {
                -100.0
            }
            val formattedLevel = String.format(Locale.getDefault(), "%.3f", level)
            csvRow.append(",").append(formattedLevel)
        }
        measurementRows.add(csvRow.toString())
        tvStatus.text = "측정이 완료되었습니다. 다음 위치 인덱스를 입력해주세요."
        Toast.makeText(this, "측정이 완료되었습니다!", Toast.LENGTH_SHORT).show()
        etIndex.text.clear()
        btnFinish.isEnabled = true
    }

    private fun saveCsvFile() {
        val fileNameInput = etFileName.text.toString()
        if (fileNameInput.isEmpty() || measurementRows.isEmpty() || dynamicAllowedWifiSSIDs == null) {
            Toast.makeText(this, "파일명 또는 측정 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        // CSV 헤더를 "Index"와 동적으로 선택된 Wi-Fi SSID로 구성합니다.
        val header = "Index," + dynamicAllowedWifiSSIDs!!.joinToString(",") + "\n"
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val currentDateTime = sdf.format(Date())
        val fileName = "${fileNameInput}_${currentDateTime}_Radiomap.csv"

        try {
            val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsFolder, fileName)
            val writer = FileWriter(file)
            writer.write(header)
            for (row in measurementRows) {
                writer.write(row + "\n")
            }
            writer.flush()
            writer.close()
            Toast.makeText(this, "CSV 파일이 저장되었습니다: $fileName", Toast.LENGTH_LONG).show()
            measurementRows.clear()
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
