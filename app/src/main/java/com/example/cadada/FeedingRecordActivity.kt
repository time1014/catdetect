package com.example.cadada

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class FeedingRecordsActivity : AppCompatActivity() {
    private lateinit var monthTextView: TextView
    private lateinit var calendarGridView: GridView
    private var calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feeding_record) // ✅ xml 파일명 확인!

        monthTextView = findViewById(R.id.tv_month)
        calendarGridView = findViewById(R.id.gv_calendar)
        val btnPrev = findViewById<Button>(R.id.btn_prev)
        val btnNext = findViewById<Button>(R.id.btn_next)

        updateCalendar() // 달력 업데이트 메서드 호출

        btnPrev.setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        btnNext.setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateCalendar()
        }


        calendarGridView.setOnItemClickListener { _, _, position, _ ->
            val selectedDay = calendarGridView.adapter.getItem(position).toString()
            if (selectedDay.isNotBlank()) {
                // 선택한 날짜에 대한 토스트 메시지 띄우기
                Toast.makeText(this, "${selectedDay}일 보고서 보여줄게요!", Toast.LENGTH_SHORT).show()

                // 선택한 날짜의 년도, 월, 일 추출
                val selectedYear = SimpleDateFormat("yyyy", Locale.KOREAN).format(calendar.time)
                val selectedMonth = SimpleDateFormat("MM", Locale.KOREAN).format(calendar.time)

                // 선택한 날짜를 DayReportActivity로 전달
                val intent = Intent(this, DayReportActivity::class.java)
                intent.putExtra("selectedDate", selectedDay) // String 값 전달
                intent.putExtra("selectedYear", selectedYear)
                intent.putExtra("selectedMonth", selectedMonth)
                startActivity(intent)
            }
        }
        val backButton: Button = findViewById(R.id.btn_back)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }

    // 달력 업데이트 메서드
    private fun updateCalendar() {
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.KOREAN)
        monthTextView.text = dateFormat.format(calendar.time)

        val tempCal = calendar.clone() as Calendar
        tempCal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) - 1

        val dayList = mutableListOf<String>()
        for (i in 0 until firstDayOfWeek) dayList.add("") // 빈칸

        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..maxDay) dayList.add(i.toString())

        val adapter = CalendarAdapter(this, dayList)
        calendarGridView.adapter = adapter
    }
}
