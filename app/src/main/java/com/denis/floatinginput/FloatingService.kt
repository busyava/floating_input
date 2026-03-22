package com.denis.floatinginput

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.json.JSONArray

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var widgetView: View? = null
    private var inputView: View? = null
    private var lastText: String = ""
    private var templates = mutableListOf<String>()
    private var templatesVisible = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        templates = loadTemplates()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createWidget()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        widgetView?.let { windowManager.removeView(it) }
        inputView?.let { windowManager.removeView(it) }
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FloatingInput",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Плавающий ввод для Termux"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, FloatingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FloatingInput")
            .setContentText("Активен")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .addAction(android.R.drawable.ic_delete, "Стоп", stopPending)
            .setOngoing(true)
            .build()
    }

    // --- Widget (2 кнопки) ---

    private fun createWidget() {
        val inflater = LayoutInflater.from(this)
        widgetView = inflater.inflate(R.layout.floating_widget, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        // Кнопка T — вставить последний текст в текущее приложение
        widgetView!!.findViewById<Button>(R.id.btnPaste).setOnClickListener {
            if (lastText.isNotEmpty()) {
                pasteToCurrentApp(lastText)
            } else {
                Toast.makeText(this, "Нет текста. Нажми ✎ для ввода", Toast.LENGTH_SHORT).show()
            }
        }

        // Кнопка ✎ — открыть/закрыть окно ввода
        widgetView!!.findViewById<Button>(R.id.btnEdit).setOnClickListener {
            if (inputView == null) {
                showInputWindow()
            } else {
                hideInputWindow()
            }
        }

        // Drag на handle
        val dragHandle = widgetView!!.findViewById<View>(R.id.dragHandle)
        setupDrag(dragHandle, widgetView!!, params)

        windowManager.addView(widgetView, params)
    }

    // --- Drag ---

    private fun setupDrag(handle: View, target: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(target, params)
                    true
                }
                else -> false
            }
        }
    }

    // --- Input window ---

    private fun showInputWindow() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingInput)
        val inflater = LayoutInflater.from(themedContext)
        inputView = inflater.inflate(R.layout.input_window, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val editText = inputView!!.findViewById<EditText>(R.id.editInput)
        val btnSend = inputView!!.findViewById<ImageButton>(R.id.btnSend)
        val btnClose = inputView!!.findViewById<ImageButton>(R.id.btnClose)
        val templatesScroll = inputView!!.findViewById<HorizontalScrollView>(R.id.templatesScroll)
        val templatesContainer = inputView!!.findViewById<LinearLayout>(R.id.templatesContainer)
        val btnToggle = inputView!!.findViewById<ImageButton>(R.id.btnToggleTemplates)

        setupTemplates(templatesContainer, editText)

        // Показать/скрыть шаблоны
        if (!templatesVisible) {
            templatesScroll.visibility = View.GONE
            btnToggle.setImageResource(android.R.drawable.arrow_down_float)
        }
        btnToggle.setOnClickListener {
            templatesVisible = !templatesVisible
            if (templatesVisible) {
                templatesScroll.visibility = View.VISIBLE
                btnToggle.setImageResource(android.R.drawable.arrow_up_float)
            } else {
                templatesScroll.visibility = View.GONE
                btnToggle.setImageResource(android.R.drawable.arrow_down_float)
            }
        }

        // Отправить: копировать текст → Termux
        btnSend.setOnClickListener {
            val text = editText.text.toString().trim()
            if (text.isNotEmpty()) {
                lastText = text
                sendToTermux(text)
                editText.text.clear()
                hideInputWindow()
            }
        }

        // Закрыть окно ввода
        btnClose.setOnClickListener {
            val text = editText.text.toString().trim()
            if (text.isNotEmpty()) {
                lastText = text
            }
            hideInputWindow()
        }

        windowManager.addView(inputView, params)
    }

    private fun hideInputWindow() {
        inputView?.let {
            windowManager.removeView(it)
            inputView = null
        }
    }

    // --- Шаблоны ---

    private fun loadTemplates(): MutableList<String> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val json = prefs.getString(KEY_TEMPLATES, null)
        if (json != null) {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { arr.getString(it) }.toMutableList()
        }
        return DEFAULT_TEMPLATES.toMutableList()
    }

    private fun saveTemplates() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(KEY_TEMPLATES, JSONArray(templates).toString()).apply()
    }

    private fun setupTemplates(container: LinearLayout, editText: EditText) {
        container.removeAllViews()
        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingInput)

        for ((index, template) in templates.withIndex()) {
            val btn = Button(this).apply {
                text = template
                isAllCaps = false
                textSize = 13f
                minWidth = 0
                minimumWidth = 0
                setPadding(20, 8, 20, 8)
                setBackgroundResource(R.drawable.template_button)

                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 6
                }
                layoutParams = lp

                setOnClickListener {
                    lastText = template
                    sendToTermux(template)
                    hideInputWindow()
                }

                setOnLongClickListener {
                    showTemplateMenu(themedContext, index, template, container, editText)
                    true
                }
            }
            container.addView(btn)
        }

        // Кнопка "+" — добавить шаблон
        val addBtn = Button(this).apply {
            text = "+"
            isAllCaps = false
            textSize = 16f
            minWidth = 0
            minimumWidth = 0
            setPadding(24, 8, 24, 8)
            setBackgroundResource(R.drawable.template_button)

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp

            setOnClickListener {
                showTemplateEditDialog(themedContext, null, null, container, editText)
            }
        }
        container.addView(addBtn)
    }

    private fun showTemplateMenu(
        ctx: ContextThemeWrapper, index: Int, template: String,
        container: LinearLayout, editText: EditText
    ) {
        val items = mutableListOf("Вставить в поле", "Редактировать", "Удалить")
        if (index > 0) items.add("← Влево")
        if (index < templates.size - 1) items.add("Вправо →")
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(template)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Вставить в поле" -> {
                        editText.setText(template)
                        editText.setSelection(template.length)
                    }
                    "Редактировать" -> showTemplateEditDialog(ctx, index, template, container, editText)
                    "Удалить" -> {
                        templates.removeAt(index)
                        saveTemplates()
                        setupTemplates(container, editText)
                    }
                    "← Влево" -> {
                        templates[index] = templates[index - 1].also { templates[index - 1] = template }
                        saveTemplates()
                        setupTemplates(container, editText)
                    }
                    "Вправо →" -> {
                        templates[index] = templates[index + 1].also { templates[index + 1] = template }
                        saveTemplates()
                        setupTemplates(container, editText)
                    }
                }
            }
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun showTemplateEditDialog(
        ctx: ContextThemeWrapper, index: Int?, current: String?,
        container: LinearLayout, editText: EditText
    ) {
        val input = EditText(ctx).apply {
            hint = "Команда..."
            if (current != null) {
                setText(current)
                setSelection(current.length)
            }
        }
        val frame = FrameLayout(ctx).apply {
            setPadding(48, 16, 48, 0)
            addView(input)
        }

        val title = if (index != null) "Редактировать" else "Добавить шаблон"
        val btnText = if (index != null) "Сохранить" else "Добавить"

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(frame)
            .setPositiveButton(btnText) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    if (index != null) {
                        templates[index] = text
                    } else {
                        templates.add(text)
                    }
                    saveTemplates()
                    setupTemplates(container, editText)
                }
            }
            .setNegativeButton("Отмена", null)
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    // --- Вставка текста ---

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FloatingInput", text))
    }

    /** Вставить в текущее приложение (кнопка T) */
    private fun pasteToCurrentApp(text: String) {
        copyToClipboard(text)
        val service = FloatingAccessibilityService.instance
        if (service != null) {
            val ok = service.smartPaste(text)
            if (ok) {
                Toast.makeText(this, "→ $text", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "→ $text (в буфере)", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "→ $text (в буфере, включите Accessibility)", Toast.LENGTH_SHORT).show()
        }
    }

    /** Переключиться на Termux и вставить (send / шаблоны) */
    private fun sendToTermux(text: String) {
        copyToClipboard(text)

        val termuxIntent = packageManager.getLaunchIntentForPackage("com.termux")
        if (termuxIntent != null) {
            termuxIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(termuxIntent)
        }

        FloatingAccessibilityService.instance?.smartPasteWithDelay(text, 600)

        Toast.makeText(this, "→ $text", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val CHANNEL_ID = "floating_input"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "STOP"
        private const val PREFS_NAME = "floating_input_prefs"
        private const val KEY_TEMPLATES = "templates"
        private val DEFAULT_TEMPLATES = listOf(
            "y", "n", "ls", "cd ..", "pwd",
            "git status", "git log --oneline -5",
            "/help", "/exit", "exit",
            "ssh ", "cd ", "cat ", "grep "
        )
    }
}
