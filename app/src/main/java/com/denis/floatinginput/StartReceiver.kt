package com.denis.floatinginput

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast

class StartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START -> startService(context)
            ACTION_STOP -> stopService(context)
            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    private fun startService(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(
                context,
                "FloatingInput: нет разрешения на overlay",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val svc = Intent(context, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }

    private fun stopService(context: Context) {
        context.stopService(Intent(context, FloatingService::class.java))
    }

    companion object {
        private const val TAG = "StartReceiver"
        const val ACTION_START = "com.denis.floatinginput.START"
        const val ACTION_STOP = "com.denis.floatinginput.STOP"
    }
}
