package com.denis.floatinginput

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val statusHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermission()

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                requestOverlayPermission()
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
        }

        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val btnBattery = findViewById<Button>(R.id.btnBattery)
        btnBattery.setOnClickListener { requestBatteryOptimizationExclusion() }

        val btnAutoStart = findViewById<Button>(R.id.btnAutoStart)
        if (isXiaomi()) {
            btnAutoStart.setOnClickListener { openXiaomiAutoStart() }
        } else {
            btnAutoStart.visibility = android.view.View.GONE
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        statusHandler.removeCallbacksAndMessages(null)
        statusHandler.postDelayed(object : Runnable {
            override fun run() {
                updateStatus()
                statusHandler.postDelayed(this, 3000)
            }
        }, 3000)
    }

    override fun onPause() {
        super.onPause()
        statusHandler.removeCallbacksAndMessages(null)
    }

    private fun updateStatus() {
        updateAccessibilityButton(findViewById(R.id.btnAccessibility))
        updateBatteryButton(findViewById(R.id.btnBattery))

        val statusText = findViewById<TextView>(R.id.txtStatus)
        val enabled = isAccessibilityServiceEnabled()
        val running = FloatingAccessibilityService.isAlive
        statusText.text = when {
            enabled && running -> "Автовставка: работает"
            enabled && !running -> "Автовставка: включена, но не отвечает!"
            else -> "Автовставка: выключена"
        }
        statusText.setTextColor(when {
            enabled && running -> 0xFF4CAF50.toInt()
            enabled && !running -> 0xFFFF5722.toInt()
            else -> 0xFF888888.toInt()
        })
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = "$packageName/${FloatingAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceId)
    }

    private fun updateAccessibilityButton(btn: Button) {
        if (isAccessibilityServiceEnabled()) {
            btn.text = "Автовставка включена"
            btn.isEnabled = false
        } else {
            btn.text = "Включить автовставку"
            btn.isEnabled = true
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_REQUEST)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_REQUEST
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                Toast.makeText(
                    this,
                    "Нужно разрешение на отображение поверх других приложений",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        moveTaskToBack(true)
    }

    // --- Battery optimization ---

    private fun requestBatteryOptimizationExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Откройте настройки батареи вручную", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateBatteryButton(btn: Button) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                btn.text = "Батарея: не оптимизируется"
                btn.isEnabled = false
            } else {
                btn.text = "Отключить оптимизацию батареи"
                btn.isEnabled = true
            }
        } else {
            btn.visibility = android.view.View.GONE
        }
    }

    // --- Xiaomi AutoStart ---

    private fun isXiaomi(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.MANUFACTURER.equals("Redmi", ignoreCase = true)
    }

    private fun openXiaomiAutoStart() {
        try {
            val intent = Intent()
            intent.component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть настройки автозапуска", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val OVERLAY_REQUEST = 1001
        private const val NOTIFICATION_REQUEST = 1002
    }
}
