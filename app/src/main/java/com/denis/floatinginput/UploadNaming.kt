package com.denis.floatinginput

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Чистые функции именования и путей — без зависимостей от Android, юнит-тестируемы. */
object UploadNaming {

    private val UNSAFE = Regex("[^A-Za-z0-9._-]+")
    private val MULTI_UNDERSCORE = Regex("_+")

    /** Любой символ вне [A-Za-z0-9._-] -> '_', схлопнуть и обрезать '_', пустое -> "file". */
    fun sanitize(name: String): String {
        val replaced = UNSAFE.replace(name.trim(), "_")
        val collapsed = MULTI_UNDERSCORE.replace(replaced, "_").trim('_')
        return if (collapsed.isEmpty()) "file" else collapsed
    }

    /** Детерминированное имя ГГГГММДД-ЧЧММСС-<очищенное-исходное>. */
    fun uniqueName(originalName: String, now: Date): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)
        return "$ts-${sanitize(originalName)}"
    }

    /** Полный путь к файлу на домашнем компьютере, как его видит Claude. */
    fun remotePath(uniqueName: String): String = UploadConfig.HOME_PREFIX + uniqueName
}
