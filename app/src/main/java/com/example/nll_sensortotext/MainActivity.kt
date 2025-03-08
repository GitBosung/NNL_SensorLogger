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

        var btnWifi : Button = findViewById(R.id.btnwifi)
        var btnSensorData : Button = findViewById(R.id.btnSensorData)


        btnWifi.setOnClickListener{
            val intent = Intent(this, Wifi::class.java)
            startActivity(intent)
        }

        btnSensorData.setOnClickListener{
            val intent = Intent(this, SensorData::class.java)
            startActivity(intent)
        }


    }
}
