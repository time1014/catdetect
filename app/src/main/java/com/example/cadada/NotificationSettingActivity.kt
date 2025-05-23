package com.example.cadada

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var timePicker: TimePicker
    private lateinit var saveButton: Button
    private lateinit var timeInfoText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        // UI 요소 초기화
        timePicker = findViewById(R.id.timePicker)
        saveButton = findViewById(R.id.btnSave)
        timeInfoText = findViewById(R.id.timeInfoText)

        // KST 시간대 설정
        val kstTimeZone = TimeZone.getTimeZone("Asia/Seoul")
        val kstCalendar = Calendar.getInstance(kstTimeZone)

        // TimePicker에 한국시간을 설정
        timePicker.hour = kstCalendar.get(Calendar.HOUR_OF_DAY)
        timePicker.minute = kstCalendar.get(Calendar.MINUTE)

        // 현재 한국시간 표시
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = kstTimeZone
        }
        timeInfoText.text = "현재 시간 (KST): ${sdf.format(kstCalendar.time)}"

        // 저장 버튼 클릭 리스너 설정
        saveButton.setOnClickListener {
            val hour = timePicker.hour
            val minute = timePicker.minute
            scheduleDailyCheck(hour, minute)

            // 알림 저장 완료 메시지
            Toast.makeText(this, "알림이 저장되었습니다.", Toast.LENGTH_SHORT).show()
        }
        val backButton: Button = findViewById(R.id.btn_back)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

    }

    private fun scheduleDailyCheck(hour: Int, minute: Int) {
        // Android 12 이상에서 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DailyCheckReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 한국 시간대로 알람 설정
        val kstTimeZone = TimeZone.getTimeZone("Asia/Seoul")
        val now = Calendar.getInstance(kstTimeZone)
        val alarmTime = Calendar.getInstance(kstTimeZone).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alarmTime.timeInMillis,
            pendingIntent
        )
    }

}