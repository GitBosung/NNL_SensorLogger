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

        var btnNavigate: Button = findViewById(R.id.btnNavigate)
        var btnGyroSensor : Button = findViewById(R.id.자이로)
        var btnWifi : Button = findViewById(R.id.btnwifi)
        var btnSensorData : Button = findViewById(R.id.btnSensorData)

        // 버튼 클릭 이벤트 처리
        btnNavigate.setOnClickListener {
            // 두 번째 화면으로 이동하는 Intent 생성
            val intent = Intent(this, Acceleration::class.java)
            startActivity(intent)
        }
        btnGyroSensor.setOnClickListener {
            val intent = Intent(this, GyroSensorActivity::class.java)
            startActivity(intent)
        }
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
