package com.example.nll_sensortotext

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Environment
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.os.Handler



class Wifi : AppCompatActivity() {

    private fun requestPermission(activity: Wifi) {
        if(ActivityCompat.checkSelfPermission(activity,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(activity,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            val permissions = arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
            ActivityCompat.requestPermissions(activity, permissions, 1)
        }
    }

    private fun requestLocationPermission(activity: Wifi) {
        if (ActivityCompat.checkSelfPermission(
                activity,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(
                activity,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
            ActivityCompat.requestPermissions(activity, permissions, 1)
        }
    }


    private lateinit var wifiManager: WifiManager
    private lateinit var wifiStrengthTextView: TextView
    private lateinit var wifiNameTextView: TextView
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiStrengthTextView = findViewById(R.id.wifiStrengthTextView)
        wifiNameTextView = findViewById(R.id.wifiNameTextView)

        // Call the requestLocationPermission method to check and request location permissions

        requestPermission(this)

        val filter = IntentFilter(WifiManager.RSSI_CHANGED_ACTION)

        registerReceiver(wifiReceiver, filter)
        requestLocationPermission(this)
        // Schedule the task to log Wi-Fi info every 5 seconds
        handler.postDelayed(logWifiRunnable, 5000)
    }


    private val logWifiRunnable = object : Runnable {
        override fun run() {

            updateWifiInfo()
            // Schedule the task again after 5 seconds
            handler.postDelayed(this, 5000)
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateWifiInfo() {
        val wifiInfo: WifiInfo? = wifiManager.connectionInfo
        val wifiStrength = wifiInfo?.rssi ?: 0
        val wifiName = wifiInfo?.ssid ?: "Unknown"
        val currentTime = System.currentTimeMillis()
        val formattedDateTime = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(currentTime)
        val wifiStrengthHz = wifiStrength / 50
        val scanResults = wifiManager.scanResults.take(5) // Get info about up to 5 nearby Wi-Fi networks


        // Construct the tabular format string
        val tableFormat = StringBuilder()
        tableFormat.append("Time\tConnected WiFi\tStrength (Hz)\t\n")
        tableFormat.append("$formattedDateTime\t$wifiName\t$wifiStrengthHz\t\n")

        for (result in scanResults) {
            tableFormat.append("$formattedDateTime\t${result.SSID}\t${result.level / 50}\t\n")
        }

        // Display or log the tabular format
        wifiStrengthTextView.text = tableFormat.toString()

        // Log the information to a file
        logWifiInfo(formattedDateTime, tableFormat.toString())
    }

    private fun logWifiInfo(dateTime: String, wifiInfo: String) {
        try {
            val fileName = "wifi_info_log.txt"
            val filePath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), fileName)
            val writer = FileWriter(filePath, true)
            writer.append("$dateTime\n$wifiInfo\n")
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.RSSI_CHANGED_ACTION) {
                updateWifiStrength()
            }
        }
    }

    private fun updateWifiStrength() {
        val wifiInfo: WifiInfo? = wifiManager.connectionInfo
        val wifiStrength = wifiInfo?.rssi ?: 0
        val wifiName = wifiInfo?.ssid ?: "Unknown"
        val currentTime = System.currentTimeMillis()
        val formattedDateTime = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(currentTime)
        val wifiStrengthHz = wifiStrength / 50

        wifiStrengthTextView.text = "와이파이 세기: $wifiStrengthHz Hz"
        wifiNameTextView.text = "와이파이 이름: $wifiName"

        // 로그 파일에 년/월/일/시간/분/초/와이파이 이름/세기 정보 추가
        logWifiStrength(formattedDateTime, wifiName, wifiStrengthHz)
    }

    private fun logWifiStrength(dateTime: String, wifiName: String, wifiStrength: Int) {
        try {
            val fileName = "wifi_strength_log.txt"
            val filePath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), fileName)
            val writer = FileWriter(filePath, true)
            writer.append("$dateTime $wifiName $wifiStrength Hz\n")
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(logWifiRunnable)
        unregisterReceiver(wifiReceiver)
        super.onDestroy()
    }
}

