package com.example.nll_sensortotext

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.BufferedWriter
import java.io.File
import java.util.*

class ArduinoLoggingActivity : AppCompatActivity() {

    private lateinit var bleLog: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private val handler = Handler(Looper.getMainLooper())

    private val DEVICE_NAME = "Nano33BLE-Logger"
    private val SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_WRITE_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_NOTIFY_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb")

    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var csvFile: File? = null
    private var csvWriter: BufferedWriter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arduino_logging)

        bleLog = findViewById(R.id.tvBLELog)
        btnConnect = findViewById(R.id.btnConnectBLE)
        btnStart = findViewById(R.id.btnStartLogging)
        btnStop = findViewById(R.id.btnStopLogging)

        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        btnConnect.setOnClickListener { startBLEScan() }
        btnStart.setOnClickListener { sendStartCommand() }
        btnStop.setOnClickListener { sendStopCommand() }

        checkPermissions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            var allGranted = true
            for (i in permissions.indices) {
                val permission = permissions[i]
                val granted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                val rationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission)

                Log.d("권한체크", "권한: $permission, 결과: ${if (granted) "허용됨" else "거부됨"}, rationale: $rationale")

                if (!granted) {
                    allGranted = false
                    if (!rationale) {
                        showPermissionDeniedDialog()
                        return
                    }
                }
            }

            if (allGranted) {
                logText("\uD83D\uDCF2 모든 권한 허용됨! BLE 시작 가능")
            } else {
                logText("❗ 일부 권한이 거부되었습니다. 기능 제한")
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("권한 필요")
            .setMessage("BLE 기능을 사용하려면 필수 권한이 필요합니다.\n앱 설정에서 권한을 직접 허용해주세요.")
            .setPositiveButton("설정 열기") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri: Uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 100)
        } else {
            logText("모든 권한이 허용되었습니다.")
        }
    }

    private fun startBLEScan() {
        // BLE 스캔 권한 체크
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) {
            logText("❗ BLE 스캔 권한이 없습니다.")
            return
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        val filter = ScanFilter.Builder().setDeviceName(DEVICE_NAME).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
        logText("BLE 스캔 시작...")

        handler.postDelayed({ scanner?.stopScan(scanCallback) }, 10000) // 10초 후 스캔 종료
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name == DEVICE_NAME) {
                logText("BLE 장치 발견: ${result.device.name}")
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                connectToDevice(result.device)
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        // BLE 연결 권한 체크
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            logText("❗ BLE 연결 권한이 없습니다.")
            return
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        logText("BLE 연결 중...")
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logText("BLE 연결 완료!")
                gatt?.requestMtu(100)
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val service = gatt?.getService(SERVICE_UUID)
            writeCharacteristic = service?.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
            val notifyChar = service?.getCharacteristic(CHARACTERISTIC_NOTIFY_UUID)

            if (notifyChar != null && ActivityCompat.checkSelfPermission(this@ArduinoLoggingActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                gatt.setCharacteristicNotification(notifyChar, true)
                val descriptor = notifyChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                logText("Notify 설정 완료!")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            val rawBytes = characteristic?.value
            val data = rawBytes?.let { String(it, Charsets.UTF_8) }
            data?.let {
                logText("수신: $it")
                csvWriter?.let { writer ->
                    try {
                        writer.write("$it\n")
                        writer.flush()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        logText("CSV 파일 쓰기 에러: ${e.message}")
                    }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logText("✅ 명령 전송 성공 (onCharacteristicWrite)")
            } else {
                logText("❌ 명령 전송 실패: status=$status")
            }
        }
    }


    private fun sendStartCommand() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            logText("BLUETOOTH_CONNECT 권한이 필요합니다.")
            return
        }
        val command = "start"
        writeCharacteristic?.let {
            it.value = command.toByteArray()
            try {
                bluetoothGatt?.writeCharacteristic(it)
                logText("\"start\" 명령 전송 완료")
                startCsvLogging()
            } catch (e: SecurityException) {
                logText("보안 예외 발생: ${e.message}")
            }
        } ?: logText("Write 캐릭터리스틱을 찾을 수 없습니다.")
    }

    private fun sendStopCommand() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            logText("BLUETOOTH_CONNECT 권한이 필요합니다.")
            return
        }
        val command = "stop"
        writeCharacteristic?.let {
            it.value = command.toByteArray()
            try {
                bluetoothGatt?.writeCharacteristic(it)
                logText("\"stop\" 명령 전송 완료")
                stopCsvLogging()
            } catch (e: SecurityException) {
                logText("보안 예외 발생: ${e.message}")
            }
        } ?: logText("Write 캐릭터리스틱을 찾을 수 없습니다.")
    }

    private fun startCsvLogging() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val fileName = "sensordata_${System.currentTimeMillis()}.csv"
            csvFile = File(downloadsDir, fileName)
            csvWriter = csvFile?.bufferedWriter()
            csvWriter?.write("timestamp,ax,ay,az,gx,gy,gz\n")
            csvWriter?.flush()
            logText("CSV 파일 생성됨: $fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            logText("CSV 파일 생성 에러: ${e.message}")
        }
    }

    private fun stopCsvLogging() {
        try {
            csvWriter?.close()
            csvWriter = null
            logText("CSV 로깅 종료됨")
        } catch (e: Exception) {
            e.printStackTrace()
            logText("CSV 파일 종료 에러: ${e.message}")
        }
    }

    private fun logText(text: String) {
        runOnUiThread {
            bleLog.append("▶ $text\n")
        }
    }

    override fun onDestroy() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt?.close()
        }
        super.onDestroy()
    }
}
