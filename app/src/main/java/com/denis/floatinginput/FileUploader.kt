package com.denis.floatinginput

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

/**
 * Заливает файл (по content-Uri) в filebrowser: login -> token -> POST байтов.
 * Не знает про UI: принимает Uri и колбэк результата. Сеть на фоновом потоке,
 * колбэк — на main.
 */
class FileUploader(private val context: Context) {

    data class Result(val ok: Boolean, val homePath: String?, val error: String?)

    fun upload(uri: Uri, onResult: (Result) -> Unit) {
        Thread {
            val result = try {
                doUpload(uri)
            } catch (e: Exception) {
                Log.w(TAG, "upload failed", e)
                Result(false, null, e.message ?: "ошибка сети")
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }.start()
    }

    private fun doUpload(uri: Uri): Result {
        val original = queryDisplayName(uri) ?: "file"
        val unique = UploadNaming.uniqueName(original, Date())

        val token = login() ?: return Result(false, null, "не удалось войти в filebrowser")

        val code = putFile(uri, unique, token)
        return if (code in 200..299) {
            Result(true, UploadNaming.remotePath(unique), null)
        } else {
            Result(false, null, "сервер вернул $code")
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx)
                }
            }
        return uri.lastPathSegment
    }

    private fun login(): String? {
        val conn = (URL("${UploadConfig.BASE_URL}/api/login").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            val body = JSONObject()
                .put("username", UploadConfig.USERNAME)
                .put("password", UploadConfig.PASSWORD)
                .put("recaptcha", "")
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) {
                Log.w(TAG, "login HTTP ${conn.responseCode}")
                null
            } else {
                conn.inputStream.bufferedReader().readText().trim().ifEmpty { null }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun putFile(uri: Uri, uniqueName: String, token: String): Int {
        // filebrowser: POST /api/resources/<path>?override=true, тело = сырые байты файла
        val rawPath = "${UploadConfig.REMOTE_DIR}/$uniqueName"
        val encodedPath = Uri.encode(rawPath, "/")    // кодируем, но сохраняем '/'
        val conn = (URL("${UploadConfig.BASE_URL}/api/resources/$encodedPath?override=true")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("X-Auth", token)
            setChunkedStreamingMode(0)               // потоково, не держим файл в памяти
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                conn.outputStream.use { output -> input.copyTo(output) }
            } ?: return -1
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "FileUploader"
    }
}
