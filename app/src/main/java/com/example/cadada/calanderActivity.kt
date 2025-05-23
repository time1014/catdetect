package com.example.cadada

import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class CalendarActivity : AppCompatActivity() {

    private lateinit var tvMonth: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var gvCalendar: GridView

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feeding_record)

        tvMonth = findViewById(R.id.tv_month)
        btnPrev = findViewById(R.id.btn_prev)
        btnNext = findViewById(R.id.btn_next)
        gvCalendar = findViewById(R.id.gv_calendar)

        updateCalendar()

        btnPrev.setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        btnNext.setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateCalendar()
        }

        gvCalendar.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val clickedDate = gvCalendar.getItemAtPosition(position) as String

            if (clickedDate.isNotBlank()) {
                Toast.makeText(this, "📅 $clickedDate 공지를 보여줄게요!", Toast.LENGTH_SHORT).show()

                // 👉 여기에 공지 다이얼로그 등 띄우는 코드 작성 가능
            }
        }
    }

    private fun updateCalendar() {
        val daysList = mutableListOf<String>()

        val tempCalendar = calendar.clone() as Calendar
        tempCalendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK) - 1

        for (i in 0 until firstDayOfWeek) {
            daysList.add("") // 빈 칸
        }

        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (day in 1..maxDay) {
            tempCalendar.set(Calendar.DAY_OF_MONTH, day)
            daysList.add(dayFormat.format(tempCalendar.time))
        }

        tvMonth.text = dateFormat.format(calendar.time)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, daysList)
        gvCalendar.adapter = adapter
    }
}
