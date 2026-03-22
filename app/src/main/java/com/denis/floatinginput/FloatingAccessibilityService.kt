package com.denis.floatinginput

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FloatingAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        // Enable InputMethod for direct text input (Termux, etc.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val info = serviceInfo
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
            serviceInfo = info
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Вставить текст наилучшим способом:
     * 1. ACTION_PASTE на focused EditText (Google Keep и т.д.)
     * 2. commitText через InputConnection (Termux и кастомные View)
     */
    fun smartPaste(text: String): Boolean {
        val root = rootInActiveWindow
        if (root != null) {
            // Strategy 1: ACTION_PASTE on focused input (EditText)
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                val result = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                focused.recycle()
                root.recycle()
                if (result) return true
            } else {
                root.recycle()
            }
        }

        // Strategy 2: type via InputConnection (Termux, custom views)
        return typeText(text)
    }

    /**
     * Ввести текст напрямую через InputConnection.
     * Работает с Termux и другими приложениями с кастомными View.
     */
    private fun typeText(text: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val im = inputMethod ?: return false
            val ic = im.currentInputConnection ?: return false
            ic.commitText(text, 1, null)
            return true
        }
        return false
    }

    fun smartPasteWithDelay(text: String, delayMs: Long = 500) {
        Handler(Looper.getMainLooper()).postDelayed({ smartPaste(text) }, delayMs)
    }

    companion object {
        var instance: FloatingAccessibilityService? = null
            private set
    }
}
