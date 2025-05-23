package com.example.cadada

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class GraphActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var tvWeekLabel: TextView
    private var currentWeekOffset = 0 // 0 = 이번 주

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.graph)

        chart = findViewById(R.id.lineChart)
        tvWeekLabel = findViewById(R.id.tvWeekLabel)

        findViewById<Button>(R.id.btnPrevWeek).setOnClickListener {
            currentWeekOffset--
            fetchGraphData()
        }

        findViewById<Button>(R.id.btnNextWeek).setOnClickListener {
            currentWeekOffset++
            fetchGraphData()
        }

        fetchGraphData()

        val backButton: Button = findViewById(R.id.btn_back)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

    }

    private fun fetchGraphData() {
        // 날짜 계산
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, currentWeekOffset)
        val year = calendar.get(Calendar.YEAR)
        val week = calendar.get(Calendar.WEEK_OF_YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // 월은 0부터 시작하므로 1을 더해줍니다.
        val weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH)
        tvWeekLabel.text = "${year}년 ${month}월 ${weekOfMonth}주"

        val url = "http://10.0.2.2:3000/api/weekly-counts?year=$year&week=$week"

        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GraphActivity", "데이터 요청 실패", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("GraphActivity", "서버 오류: ${response.code}")
                    return
                }

                val json = JSONArray(response.body?.string())
                val entries = ArrayList<Entry>()
                val labels = ArrayList<String>()

                // 한국시간(KST)에 맞춰서 날짜 그대로 사용
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
                val outputFormat = SimpleDateFormat("MM-dd", Locale.KOREA)

                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    val dateStr = obj.getString("date") // yyyy-MM-dd
                    val count = obj.getInt("total_count")

                    try {
                        // 이미 KST로 저장된 날짜 그대로 사용
                        val date = inputFormat.parse(dateStr)
                        val label = outputFormat.format(date ?: Date())

                        labels.add(label)
                        entries.add(Entry(i.toFloat(), count.toFloat()))
                    } catch (e: Exception) {
                        Log.e("GraphActivity", "날짜 변환 오류: $dateStr", e)
                    }
                }

                runOnUiThread {
                    val dataSet = LineDataSet(entries, "고양이 탐지 수")
                    dataSet.valueTextSize = 12f
                    val lineData = LineData(dataSet)

                    val xAxis = chart.xAxis
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.granularity = 1f
                    xAxis.setDrawLabels(true)
                    xAxis.valueFormatter = IndexAxisValueFormatter(labels)

                    val yAxis = chart.axisLeft
                    yAxis.setGranularity(1f) // Y축 간격을 1로 설정
                    yAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return value.toInt().toString() // 정수만 출력
                        }
                    }


                    chart.axisRight.isEnabled = false
                    chart.description.isEnabled = false
                    chart.data = lineData
                    chart.invalidate()
                }
            }
        })
    }
}
