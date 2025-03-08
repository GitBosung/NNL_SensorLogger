package com.example.nll_sensortotext

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class GyroSensorActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gyroscopeSensor: Sensor? = null

    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gyro_sensor)

        // TextView 초기화
        textView = findViewById(R.id.textView)

        // SensorManager 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // 자이로스코프 센서 초기화
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // 자이로스코프 센서 리스너 등록
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // 자이로스코프 센서 값이 변경되었을 때 호출됩니다.
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            // X, Y, Z 축의 회전 속도 값 가져오기
            val xRotationRate = event.values[0]
            val yRotationRate = event.values[1]
            val zRotationRate = event.values[2]

            // 화면에 출력
            val message = "X Rotation Rate: $xRotationRate\nY Rotation Rate: $yRotationRate\nZ Rotation Rate: $zRotationRate"
            textView.text = message
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 센서 정확도 변경 시 호출됩니다.
    }

    override fun onDestroy() {
        // 액티비티가 종료될 때 센서 리스너 등록 해제
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }
}
