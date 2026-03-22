package com.denis.floatinginput

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager

class MainActivity : AppCompatActivity() {

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
        updateAccessibilityButton(btnAccessibility)
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityButton(findViewById(R.id.btnAccessibility))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
            it.resolveInfo.serviceInfo.name == FloatingAccessibilityService::class.java.name
        }
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

    companion object {
        private const val OVERLAY_REQUEST = 1001
        private const val NOTIFICATION_REQUEST = 1002
    }
}
