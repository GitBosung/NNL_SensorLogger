package com.example.nll_sensortotext

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanResult as BLEScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class ArduinoLoggingActivity : AppCompatActivity() {

    private lateinit var bleLog: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var csvFile: File? = null
    private var csvWriter: BufferedWriter? = null

    private val handler = Handler(Looper.getMainLooper())

    private val DEVICE_NAME = "Nano33BLE-Logger"
    private val SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_WRITE_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_NOTIFY_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb")

    // --- WiFi 로깅 관련 변수 ---
    private var wifiManager: WifiManager? = null
    private val wifiHandler = Handler(Looper.getMainLooper())

    private val wifiRunnable = object : Runnable {
        override fun run() {
            if (ActivityCompat.checkSelfPermission(
                    this@ArduinoLoggingActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                logText("❗ WiFi 스캔 권한이 없습니다.")
                return
            }
            wifiManager?.startScan()
            wifiHandler.postDelayed(this, 4000)
        }
    }

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (!success) return

                val results = wifiManager?.scanResults ?: return
                val timestamp = System.currentTimeMillis()
                // 센서 데이터 칸 비우기 (6개)
                val emptySensorFields = List(6) { "" }.joinToString(",")
                // BSSID:RSSI 쌍 모두 기록
                val wifiList = results.joinToString(",") { "${it.BSSID}:${it.level}" }
                val wifiRow = "$timestamp,wifi,$emptySensorFields,$wifiList\n"

                try {
                    csvWriter?.write(wifiRow)
                    csvWriter?.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                    logText("CSV 파일 WiFi 쓰기 에러: ${e.message}")
                }
            }
        }
    }
    // --- // WiFi 로깅 관련 변수 ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arduino_logging)

        bleLog     = findViewById(R.id.tvBLELog)
        btnConnect = findViewById(R.id.btnConnectBLE)
        btnStart   = findViewById(R.id.btnStartLogging)
        btnStop    = findViewById(R.id.btnStopLogging)

        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        // WifiManager 초기화 및 스캔 리시버 등록
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        registerReceiver(
            wifiScanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )

        btnConnect.setOnClickListener { startBLEScan() }
        btnStart.setOnClickListener   { sendStartCommand() }
        btnStop.setOnClickListener    { sendStopCommand() }

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // WiFi 스캔 권한
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this,
                permissionsToRequest.toTypedArray(), 100)
        } else {
            logText("모든 권한이 허용되었습니다.")
        }
    }

    private fun startBLEScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            logText("❗ BLE 스캔 권한이 없습니다.")
            return
        }
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val filter   = ScanFilter.Builder().setDeviceName(DEVICE_NAME).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, bleScanCallback)
        logText("BLE 스캔 시작...")
        handler.postDelayed({ scanner.stopScan(bleScanCallback) }, 10000)
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: BLEScanResult) {
            if (result.device.name == DEVICE_NAME) {
                logText("BLE 장치 발견: ${result.device.name}")
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                connectToDevice(result.device)
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            logText("❗ BLE 연결 권한이 없습니다.")
            return
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        logText("BLE 연결 중...")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?, status: Int, newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logText("BLE 연결 완료!")
                gatt?.requestMtu(100)
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val service    = gatt?.getService(SERVICE_UUID)
            writeCharacteristic = service
                ?.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
            val notifyChar = service
                ?.getCharacteristic(CHARACTERISTIC_NOTIFY_UUID)

            if (notifyChar != null &&
                ActivityCompat.checkSelfPermission(
                    this@ArduinoLoggingActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                gatt.setCharacteristicNotification(notifyChar, true)
                val descriptor = notifyChar.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                descriptor?.value = BluetoothGattDescriptor
                    .ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                logText("Notify 설정 완료!")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?
        ) {
            val rawBytes  = characteristic?.value
            val sensorData = rawBytes?.let { String(it, Charsets.UTF_8) }
            sensorData?.let {
                val timestamp = System.currentTimeMillis()
                // 센서 데이터 6개(ax,ay,az,gx,gy,gz) 기록
                val row = "$timestamp,sensor,$it\n"
                try {
                    csvWriter?.write(row)
                    csvWriter?.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                    logText("CSV 센서 쓰기 에러: ${e.message}")
                }
                logText("수신: $it")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logText("✅ 명령 전송 성공")
            } else {
                logText("❌ 명령 전송 실패: status=$status")
            }
        }
    }

    private fun sendStartCommand() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            logText("BLUETOOTH_CONNECT 권한 필요")
            return
        }
        val command = "start"
        writeCharacteristic?.let {
            it.value = command.toByteArray()
            try {
                bluetoothGatt?.writeCharacteristic(it)
                logText("\"start\" 명령 전송 완료")
                startCsvLogging()
                wifiHandler.post(wifiRunnable)
            } catch (e: SecurityException) {
                logText("보안 예외: ${e.message}")
            }
        } ?: logText("Write 캐릭터리스틱 없음")
    }

    private fun sendStopCommand() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            logText("BLUETOOTH_CONNECT 권한 필요")
            return
        }
        val command = "stop"
        writeCharacteristic?.let {
            it.value = command.toByteArray()
            try {
                bluetoothGatt?.writeCharacteristic(it)
                logText("\"stop\" 명령 전송 완료")
                wifiHandler.removeCallbacks(wifiRunnable)
                stopCsvLogging()
            } catch (e: SecurityException) {
                logText("보안 예외: ${e.message}")
            }
        } ?: logText("Write 캐릭터리스틱 없음")
    }

    private fun startCsvLogging() {
        try {
            val downloadsDir = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val fileName = "sensordata_${System.currentTimeMillis()}.csv"
            csvFile   = File(downloadsDir, fileName)
            csvWriter = csvFile?.bufferedWriter()
            // 헤더: timestamp,logType,clock,ax,ay,az,gx,gy,gz,wifiData
            csvWriter?.write("timestamp,logType,clock,ax,ay,az,gx,gy,gz,wifiData\n")
            csvWriter?.flush()
            logText("CSV 파일 생성: $fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            logText("CSV 생성 에러: ${e.message}")
        }
    }

    private fun stopCsvLogging() {
        try {
            csvWriter?.close()
            csvWriter = null
            logText("CSV 로깅 종료")
        } catch (e: Exception) {
            e.printStackTrace()
            logText("CSV 종료 에러: ${e.message}")
        }
    }

    private fun logText(text: String) {
        runOnUiThread {
            bleLog.append("▶ $text\n")
        }
    }

    override fun onDestroy() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothGatt?.close()
        }
        unregisterReceiver(wifiScanReceiver)
        super.onDestroy()
    }
}
