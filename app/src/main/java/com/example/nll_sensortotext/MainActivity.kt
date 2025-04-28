package com.example.nll_sensortotext

// MainActivity.kt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity



class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val btnWifi : Button = findViewById(R.id.btnwifi)
        val btnSensorData : Button = findViewById(R.id.btnSensorData)
        val btnArduino: Button = findViewById(R.id.btnArduino)
        val btnSensorRadiomap: Button = findViewById(R.id.btnSensorRadiomap)


        btnWifi.setOnClickListener{
            val intent = Intent(this, Make_RadioMap::class.java)
            startActivity(intent)
        }

        btnSensorData.setOnClickListener{
            val intent = Intent(this, SensorData::class.java)
            startActivity(intent)
        }

        btnArduino.setOnClickListener {
            val intent = Intent(this, ArduinoLoggingActivity::class.java)
            startActivity(intent)
        }

        btnSensorRadiomap.setOnClickListener {  // 추가!
            val intent = Intent(this, Make_Sensor_RadioMap::class.java)
            startActivity(intent)
        }


    }
}
