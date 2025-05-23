package com.example.cadada

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class DailyCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("DailyCheckReceiver", "알람 리시버 작동함")

        val queue = Volley.newRequestQueue(context)
        val url = "http://10.0.2.2:3000/api/yesterday-report"

        val stringRequest = StringRequest(Request.Method.GET, url, { response ->
            try {
                val jsonResponse = JSONObject(response)
                val count = jsonResponse.getInt("count")
                Log.d("DailyCheckReceiver", "어제 고양이 수: $count")

                if (count <= 1 || count >= 5) {
                    sendNotification(context)
                } else {
                    Log.d("DailyCheckReceiver", "알림 조건 미충족. 알림 안 보냄")
                }
            } catch (e: Exception) {
                Log.e("DailyCheckReceiver", "응답 처리 실패: ${e.message}")
            }
        }, { error ->
            Log.e("DailyCheckReceiver", "서버 요청 실패: ${error.message}")
        })

        queue.add(stringRequest)
    }

    private fun sendNotification(context: Context?) {
        context ?: return
        val channelId = "daily_check_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Check Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "이상치 감지 알림 채널"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("이상치 감지")
            .setContentText("어제의 고양이 수가 너무 적거나 많습니다.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("어제의 고양이의 섭취횟수가 너무 적거나 많습니다\n기록을 확인해 주세요."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }
}