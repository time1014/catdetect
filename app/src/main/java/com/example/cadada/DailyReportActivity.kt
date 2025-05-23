package com.example.cadada

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class DailyReportActivity : AppCompatActivity() {

    private lateinit var textViewTitle: TextView
    private lateinit var textViewTodayDate: TextView
    private lateinit var dynamicContentLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.daily_report)

        textViewTitle = findViewById(R.id.textViewTitle)
        textViewTodayDate = findViewById(R.id.textViewTodayDate)
        dynamicContentLayout = findViewById(R.id.dynamicContentLayout)

        val kstCalendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(kstCalendar.time)
        val formattedDate = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA).format(kstCalendar.time)
        textViewTodayDate.text = formattedDate

        fetchDailyReport(today)

        val backButton: Button = findViewById(R.id.btn_back)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

    }

    private fun fetchDailyReport(date: String) {
        val url = "http://10.0.2.2:3000/api/daily-report?date=$date"
        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DailyReportActivity", "서버 요청 실패", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("DailyReportActivity", "응답 실패: ${response.code}")
                    return
                }

                val json = JSONArray(response.body?.string())

                runOnUiThread {
                    dynamicContentLayout.removeAllViews()

                    if (json.length() == 0) {
                        val emptyText = TextView(this@DailyReportActivity).apply {
                            text = "오늘 감지된 고양이 기록이 없습니다."
                            textSize = 18f
                        }
                        dynamicContentLayout.addView(emptyText)
                    } else {
                        var totalCount = 0

                        for (i in 0 until json.length()) {
                            val obj = json.getJSONObject(i)
                            val time = obj.getString("time")
                            val snapshot = obj.getString("snapshot")
                            val count = obj.getInt("count")
                            totalCount += count

                            val formattedTime = formatTime(time)

                            val timeText = TextView(this@DailyReportActivity).apply {
                                text = "⏰ 섭취 확인 시간: $formattedTime"
                                textSize = 16f
                            }

                            val imageView = ImageView(this@DailyReportActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    600
                                )
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }

                            Glide.with(this@DailyReportActivity)
                                .load(snapshot)
                                .into(imageView)

                            // ✅ 박스 컨테이너
                            val containerLayout = LinearLayout(this@DailyReportActivity).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(24, 24, 24, 24)
                                background = resources.getDrawable(R.drawable.box_background, null)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(0, 0, 0, 32)  // 아래 간격
                                }
                                addView(timeText)
                                addView(imageView)
                            }

                            dynamicContentLayout.addView(containerLayout)
                        }

                        val statusText = TextView(this@DailyReportActivity).apply {
                            textSize = 18f
                            text = if (totalCount in 2..4) {
                                "✅ 섭취횟수가 정상 범위입니다. (총 $totalCount 회)"
                            } else {
                                "⚠️ 섭취횟수의 이상이 확인되었습니다. (총 $totalCount 회)"
                            }
                            setTextColor(if (totalCount in 2..4) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
                        }

                        dynamicContentLayout.addView(statusText, 0)
                    }
                }
            }
        })
    }

    private fun formatTime(time: String): String {
        val originalFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
        val parsedDate = originalFormat.parse(time)
        val newFormat = SimpleDateFormat("a h시 mm분", Locale.KOREA)
        return newFormat.format(parsedDate)
    }
}