package com.denis.floatinginput

/**
 * Конфигурация загрузки файлов в filebrowser. Debug-сборка для личного
 * использования — секреты в коде приемлемы, держим их в одном месте.
 */
object UploadConfig {
    const val BASE_URL = "https://files.vkrutina.online"
    const val USERNAME = "sshhallwss"
    const val PASSWORD = "Kashatka123."
    // scope аккаунта = корень со всеми шарами (downloads/media/scans/share/workshop),
    // поэтому путь включает имя шары workshop
    const val REMOTE_DIR = "workshop/claude-inbox"
    const val HOME_PREFIX = "/home/denis/truenas/workshop/claude-inbox/"
}
