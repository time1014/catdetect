package com.example.cadada

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class DayReportActivity : AppCompatActivity() {

    private lateinit var textViewTitle: TextView
    private lateinit var textViewTodayDate: TextView
    private lateinit var dynamicContentLayout: LinearLayout

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dayreport)

        textViewTitle = findViewById(R.id.tv_date)
        textViewTodayDate = findViewById(R.id.tv_feed_count)
        dynamicContentLayout = findViewById(R.id.feed_records_container)

        val selectedDate = intent.getStringExtra("selectedDate") ?: ""
        val selectedYear = intent.getStringExtra("selectedYear") ?: ""
        val selectedMonth = intent.getStringExtra("selectedMonth") ?: ""

        textViewTodayDate.text = "${selectedYear}년 ${selectedMonth}월 ${selectedDate}일"

        val dateParam = "${selectedYear.padStart(2, '0')}-${selectedMonth.padStart(2, '0')}-${selectedDate.padStart(2, '0')}"

        fetchDailyReport(dateParam)

        // 뒤로 가기 버튼 설정
        val backButton: Button? = findViewById(R.id.btn_back)  // null 가능성 처리
        backButton?.setOnClickListener {
            finish() // 현재 Activity를 종료하고 이전 Activity로 돌아감
        }
    }

    private fun fetchDailyReport(dateParam: String) {
        val url = "http://10.0.2.2:3000/api/daily-report?date=$dateParam"
        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DayReportActivity", "서버 요청 실패", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("DayReportActivity", "응답 실패: ${response.code}")
                    return
                }

                val json = JSONArray(response.body?.string())

                runOnUiThread {
                    dynamicContentLayout.removeAllViews()

                    if (json.length() == 0) {
                        val emptyText = TextView(this@DayReportActivity).apply {
                            text = "오늘 감지된 고양이 기록이 없습니다."
                            textSize = 18f
                        }
                        dynamicContentLayout.addView(emptyText)
                    } else {
                        var totalFeedCount = 0

                        for (i in 0 until json.length()) {
                            val obj = json.getJSONObject(i)
                            val count = obj.getInt("count")
                            val time = obj.getString("time")
                            val snapshot = obj.getString("snapshot")

                            totalFeedCount += count

                            val formattedTime = formatTime(time)

                            val timeText = TextView(this@DayReportActivity).apply {
                                text = "⏰ 섭취 확인 시간: $formattedTime"
                                textSize = 16f
                            }

                            val imageView = ImageView(this@DayReportActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    600
                                )
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }

                            Glide.with(this@DayReportActivity)
                                .load(snapshot)
                                .into(imageView)

                            dynamicContentLayout.addView(timeText)
                            dynamicContentLayout.addView(imageView)
                        }

                        // ✅ 먼저 상태 텍스트를 추가 (맨 위에 표시됨)
                        val statusText = TextView(this@DayReportActivity).apply {
                            textSize = 18f
                            text = if (totalFeedCount in 2..4) {
                                "✅ 섭취횟수가 정상 범위입니다. (총 $totalFeedCount 회)"
                            } else {
                                "⚠️ 섭취횟수의 이상이 확인되었습니다. (총 $totalFeedCount 회)"
                            }
                            setTextColor(
                                if (totalFeedCount in 2..4) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
                            )
                        }
                        // 👉 가장 먼저 추가
                        dynamicContentLayout.addView(statusText, 0)

                        val feedCountText = TextView(this@DayReportActivity).apply {
                            text = "오늘의 총 섭식 횟수: $totalFeedCount"
                            textSize = 18f
                            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        }
                        dynamicContentLayout.addView(feedCountText)
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
