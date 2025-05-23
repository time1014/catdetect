package com.example.cadada

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    // 📱 UI 관련
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    // 🔘 버튼 이벤트
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🧩 UI 연결
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)

        val btnAlarm: ImageButton = findViewById(R.id.btn_alarm)
        val btnSettings: ImageButton = findViewById(R.id.btn_settings)
        val btnLiveVideo: Button = findViewById(R.id.btn_live_video)
        val btnFeedingRecords: Button = findViewById(R.id.btn_feeding_records)
        val btnDailyReport: Button = findViewById(R.id.btn_daily_report)
        val btn_Graph = findViewById<Button>(R.id.btn_graph)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        // 🎬 버튼 클릭 시 LiveSnapshotActivity로 이동
        btnLiveVideo.setOnClickListener {
            val intent = Intent(this, LiveSnapshotActivity::class.java)
            startActivity(intent)
        }

        btnAlarm.setOnClickListener {
            val intent = Intent(this, NotificationSettingsActivity::class.java)
            startActivity(intent)
        }

        // 🛠️ btnSettings 클릭 시 팝업 메뉴 표시
        btnSettings.setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            menuInflater.inflate(R.menu.drawer_menu, popupMenu.menu)  // R.menu.drawer_menu 대신 원하는 메뉴 파일로 수정

            // 팝업 메뉴 항목 클릭 처리
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.nav_settings -> {
                        // 환경설정 메뉴 클릭 시 동작
                        Toast.makeText(this, "환경설정 클릭됨", Toast.LENGTH_SHORT).show()
                        // 환경설정 화면으로 이동 (예시)
                        val intent = Intent(this, NotificationSettingsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.nav_notifications -> {
                        // 알림 메뉴 클릭 시 동작
                        Toast.makeText(this, "알림 클릭됨", Toast.LENGTH_SHORT).show()
                        // 알림 설정 화면으로 이동 (예시)
                        val intent = Intent(this, NotificationSettingsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }

        btnFeedingRecords.setOnClickListener {
            val intent = Intent(this, FeedingRecordsActivity::class.java)
            startActivity(intent)
        }

        btnDailyReport.setOnClickListener {
            val intent = Intent(this, DailyReportActivity::class.java)
            startActivity(intent)
        }

        btn_Graph.setOnClickListener {
            val intent = Intent(this, GraphActivity::class.java)
            startActivity(intent)
        }

        // 📂 메뉴 항목 클릭 처리
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> {
                    Toast.makeText(this, "환경설정 클릭됨", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_notifications -> {
                    Toast.makeText(this, "알림 클릭됨", Toast.LENGTH_SHORT).show()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }
}
