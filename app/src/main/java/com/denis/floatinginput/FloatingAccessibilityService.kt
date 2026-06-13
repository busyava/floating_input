package com.denis.floatinginput

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FloatingAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "Accessibility service connected (pid=${android.os.Process.myPid()})")
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
        Log.w(TAG, "Accessibility service destroyed")
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
     * Требует flagInputMethodEditor в accessibility_service_config.xml.
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

    /**
     * Вставить с несколькими попытками: после переключения на Termux фокус и
     * input connection появляются не мгновенно, поэтому ретраим, пока не выйдет.
     */
    fun smartPasteWithDelay(text: String, delayMs: Long = 500) {
        pasteWithRetry(text, delayMs, attemptsLeft = MAX_PASTE_ATTEMPTS)
    }

    private fun pasteWithRetry(text: String, delayMs: Long, attemptsLeft: Int) {
        Handler(Looper.getMainLooper()).postDelayed({
            val ok = smartPaste(text)
            Log.i(TAG, "paste attempt result=$ok, left=${attemptsLeft - 1}")
            if (!ok && attemptsLeft > 1) {
                pasteWithRetry(text, RETRY_DELAY, attemptsLeft - 1)
            }
        }, delayMs)
    }

    companion object {
        private const val TAG = "FloatingA11y"
        private const val MAX_PASTE_ATTEMPTS = 6
        private const val RETRY_DELAY = 350L
        var instance: FloatingAccessibilityService? = null
            private set
        val isAlive: Boolean get() = instance != null
    }
}
