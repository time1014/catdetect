package com.example.cadada

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class CalendarAdapter(
    private val context: Context,
    private val days: List<String>
) : BaseAdapter() {

    override fun getCount(): Int = days.size

    override fun getItem(position: Int): Any = days[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_calendar_day, parent, false)

        val dayText = view.findViewById<TextView>(R.id.tv_day)
        dayText.text = days[position]

        return view
    }
}
