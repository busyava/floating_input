package com.denis.floatinginput

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Невидимая activity: Service не может получить результат системного пикера,
 * поэтому тап «＋» запускает её. Открывает пикер, заливает выбранный файл,
 * кладёт домашний путь в буфер обмена и завершается.
 */
class FileUploadActivity : AppCompatActivity() {

    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        Toast.makeText(applicationContext, "Загрузка…", Toast.LENGTH_SHORT).show()
        FileUploader(this).upload(uri) { result ->
            if (result.ok && result.homePath != null) {
                copyToClipboard(result.homePath)
                Toast.makeText(applicationContext, "Путь скопирован в буфер", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(applicationContext, "Ошибка: ${result.error}", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // запускаем пикер один раз; при пересоздании не дублируем
        if (savedInstanceState == null) {
            picker.launch(arrayOf("*/*"))
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("FloatingInput path", text))
    }
}
