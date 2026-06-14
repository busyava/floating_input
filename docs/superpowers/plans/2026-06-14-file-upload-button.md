# FloatingInput «＋» File Upload Button — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить неиспользуемую кнопку шаблонов в оверлее на «＋», которая выбирает файл системным пикером, заливает его в filebrowser (`files.vkrutina.online` → шара workshop → `claude-inbox/`) под детерминированным именем и кладёт в буфер обмена путь к файлу на домашнем компьютере, как его видит Claude.

**Architecture:** `FloatingService` (Service) не может получить результат системного пикера, поэтому тап «＋» запускает прозрачную activity-прокси `FileUploadActivity`. Она открывает `ACTION_OPEN_DOCUMENT`, получает `Uri`, передаёт его в `FileUploader` (логин→токен→POST файла на filebrowser через `HttpURLConnection`), при успехе копирует домашний путь в буфер и завершается. Чистые функции именования/путей вынесены в `UploadNaming` и покрыты JVM-юнит-тестами. Транспорт — обычный HTTPS на `*.vkrutina.online` (тот же канал, что несёт hallwss; правок nginx/sshd/firewall не требуется).

**Tech Stack:** Kotlin, Android (minSdk 26, compileSdk 36), `HttpURLConnection` (без новых сетевых зависимостей), `androidx.activity` Result API (через appcompat), JUnit 4 для юнит-тестов.

---

## File Structure

| Файл | Ответственность |
|---|---|
| `app/src/main/java/com/denis/floatinginput/UploadConfig.kt` | Константы: base URL, логин/пароль апки, целевая папка, префикс домашнего пути |
| `app/src/main/java/com/denis/floatinginput/UploadNaming.kt` | Чистые функции: очистка имени, генерация уникального имени `ГГГГММДД-ЧЧММСС-<очищенное>`, сборка домашнего пути |
| `app/src/main/java/com/denis/floatinginput/FileUploader.kt` | HTTP к filebrowser: login→token→POST файла. Не знает про UI |
| `app/src/main/java/com/denis/floatinginput/FileUploadActivity.kt` | Прозрачная activity-прокси: пикер → FileUploader → буфер обмена → тост → finish |
| `app/src/test/java/com/denis/floatinginput/UploadNamingTest.kt` | JVM-юнит-тесты для UploadNaming |
| `app/src/main/res/layout/input_window.xml` | Кнопка `btnToggleTemplates` → `btnAddFile` («＋»); удаление ленты шаблонов |
| `app/src/main/java/com/denis/floatinginput/FloatingService.kt` | Подключить «＋» к запуску FileUploadActivity; выпилить код шаблонов |
| `app/src/main/AndroidManifest.xml` | `INTERNET` permission; регистрация `FileUploadActivity` |
| `app/build.gradle.kts` | `testImplementation("junit:junit:...")`; bump versionCode/Name |

---

## Task 1: Подготовка инфраструктуры filebrowser ✅ ВЫПОЛНЕНО 2026-06-14

> Выполнено и проверено сквозным тестом против реального filebrowser. Креды дал Денис; значения зашиты в `UploadConfig` (Task 3). Кодовых изменений тут нет.

- [x] **Step 1: Транспорт** — `https://files.vkrutina.online` отвечает; `POST /api/login` аккаунтом `sshhallwss` возвращает JWT. (Финальную проверку именно с рабочей сети телефона делает Денис при ручном тесте, Task 8.)
- [x] **Step 2: Папка** — создана `workshop/claude-inbox/` через `POST /api/resources/workshop/claude-inbox/` (200). Внимание: scope аккаунта `sshhallwss` — корень со всеми шарами, поэтому путь `workshop/claude-inbox`, **не** `claude-inbox`.
- [x] **Step 3: Аккаунт** — используется существующий `sshhallwss` (пароль `Kashatka123.`); права на запись подтверждены успешной заливкой.
- [x] **Step 4: Чтение на Hall** — залитый пробный файл появился в `/home/denis/truenas/workshop/claude-inbox/`, читается под `denis`. Пробник удалён.

---

## Task 2: Манифест, тестовая зависимость, версия

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Добавить INTERNET permission**

В `AndroidManifest.xml` после строки с `SYSTEM_ALERT_WINDOW` добавить:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
```

(Без неё `HttpURLConnection` бросит `SecurityException`/`UnknownHostException`.)

- [ ] **Step 2: Добавить JUnit и поднять версию**

В `app/build.gradle.kts`:

В блоке `defaultConfig` заменить версию:

```kotlin
        versionCode = 3
        versionName = "1.2"
```

В блок `dependencies` добавить:

```kotlin
    testImplementation("junit:junit:4.13.2")
```

- [ ] **Step 3: Проверить, что проект собирается и тестовый таск доступен**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (тестов пока нет — таск проходит вхолостую).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/build.gradle.kts
git commit -m "chore: INTERNET permission, junit dep, bump to 1.2"
```

---

## Task 3: UploadConfig + UploadNaming (TDD)

**Files:**
- Create: `app/src/main/java/com/denis/floatinginput/UploadConfig.kt`
- Create: `app/src/main/java/com/denis/floatinginput/UploadNaming.kt`
- Test: `app/src/test/java/com/denis/floatinginput/UploadNamingTest.kt`

- [ ] **Step 1: Написать падающий тест**

> Правило очистки (важно для ожиданий): любой символ вне `[A-Za-z0-9._-]` → `_`; подряд идущие `_` схлопываются в один; `_` по краям обрезаются; пустой результат → `"file"`. Кириллица **не транслитерируется**, а заменяется на `_` (читаемость имени вторична, важна предсказуемость пути). Поэтому `"IMG 2026 фото.jpg"` → `"IMG_2026_.jpg"`.

Create `app/src/test/java/com/denis/floatinginput/UploadNamingTest.kt`:

```kotlin
package com.denis.floatinginput

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class UploadNamingTest {

    private fun dateOf(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Date {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.clear()
        cal.set(y, mo - 1, d, h, mi, s)
        return cal.time
    }

    @Test
    fun sanitize_replaces_spaces_and_cyrillic() {
        // пробелы и кириллица -> '_', схлопывание; латиница/точка/цифры остаются
        assertEquals("IMG_2026_.jpg", UploadNaming.sanitize("IMG 2026 фото.jpg"))
    }

    @Test
    fun sanitize_keeps_safe_chars() {
        assertEquals("Report-01_v2.pdf", UploadNaming.sanitize("Report-01_v2.pdf"))
    }

    @Test
    fun sanitize_collapses_and_trims_underscores() {
        assertEquals("a_b", UploadNaming.sanitize("  a   b  "))
    }

    @Test
    fun sanitize_empty_becomes_file() {
        assertEquals("file", UploadNaming.sanitize("???"))
    }

    @Test
    fun uniqueName_has_timestamp_prefix() {
        val d = dateOf(2026, 6, 14, 9, 5, 3)
        assertEquals("20260614-090503-photo.jpg",
            UploadNaming.uniqueName("photo.jpg", d))
    }

    @Test
    fun remotePath_uses_home_prefix() {
        assertEquals(
            "/home/denis/truenas/workshop/claude-inbox/20260614-090503-photo.jpg",
            UploadNaming.remotePath("20260614-090503-photo.jpg")
        )
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL — `Unresolved reference: UploadNaming`.

- [ ] **Step 3: Создать UploadConfig**

Create `app/src/main/java/com/denis/floatinginput/UploadConfig.kt`:

```kotlin
package com.denis.floatinginput

/**
 * Конфигурация загрузки файлов в filebrowser. Debug-сборка для личного
 * использования — секреты в коде приемлемы, держим их в одном месте.
 * Логин/пароль заполняются после Task 1 (создание аккаунта filebrowser).
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
```

- [ ] **Step 4: Создать UploadNaming**

Create `app/src/main/java/com/denis/floatinginput/UploadNaming.kt`:

```kotlin
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
```

- [ ] **Step 5: Запустить тест — убедиться, что проходит**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (все тесты UploadNamingTest зелёные).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/denis/floatinginput/UploadConfig.kt \
        app/src/main/java/com/denis/floatinginput/UploadNaming.kt \
        app/src/test/java/com/denis/floatinginput/UploadNamingTest.kt
git commit -m "feat: UploadConfig + UploadNaming with unit tests"
```

---

## Task 4: FileUploader

**Files:**
- Create: `app/src/main/java/com/denis/floatinginput/FileUploader.kt`

> HTTP-логика без UI. Тестируется вручную (сетевой код против реального filebrowser в Task 8). Юнит-тест сети не пишем — YAGNI, потребовал бы мок-сервер; чистая логика уже покрыта в Task 3.

- [ ] **Step 1: Создать FileUploader**

Create `app/src/main/java/com/denis/floatinginput/FileUploader.kt`:

```kotlin
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
```

- [ ] **Step 2: Проверить компиляцию**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/denis/floatinginput/FileUploader.kt
git commit -m "feat: FileUploader — login + POST file to filebrowser"
```

---

## Task 5: FileUploadActivity + регистрация в манифесте

**Files:**
- Create: `app/src/main/java/com/denis/floatinginput/FileUploadActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Создать прозрачную activity-прокси**

Create `app/src/main/java/com/denis/floatinginput/FileUploadActivity.kt`:

```kotlin
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
        Toast.makeText(this, "Загрузка…", Toast.LENGTH_SHORT).show()
        FileUploader(this).upload(uri) { result ->
            if (result.ok && result.homePath != null) {
                copyToClipboard(result.homePath)
                Toast.makeText(this, "Путь скопирован в буфер", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Ошибка: ${result.error}", Toast.LENGTH_LONG).show()
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
```

- [ ] **Step 2: Зарегистрировать activity в манифесте**

В `app/src/main/AndroidManifest.xml`, внутри `<application>`, после блока `<activity android:name=".MainActivity">…</activity>` добавить:

```xml
        <activity
            android:name=".FileUploadActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />
```

- [ ] **Step 3: Проверить сборку**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/denis/floatinginput/FileUploadActivity.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat: transparent FileUploadActivity launching system picker"
```

---

## Task 6: Лейаут — кнопка «＋» вместо ленты шаблонов

**Files:**
- Modify: `app/src/main/res/layout/input_window.xml`

- [ ] **Step 1: Переписать input_window.xml**

Полностью заменить содержимое `app/src/main/res/layout/input_window.xml` на (удалены `HorizontalScrollView`/`templatesContainer`; `btnToggleTemplates` → `btnAddFile` с иконкой «+»):

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@drawable/input_background"
    android:padding="12dp"
    android:elevation="16dp">

    <!-- Поле ввода + кнопки -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <ImageButton
            android:id="@+id/btnAddFile"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Отправить файл"
            android:scaleType="centerInside"
            android:src="@android:drawable/ic_input_add" />

        <EditText
            android:id="@+id/editInput"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="Команда..."
            android:inputType="textMultiLine"
            android:maxLines="5"
            android:padding="12dp"
            android:background="@drawable/edit_background"
            android:textSize="16sp" />

        <ImageButton
            android:id="@+id/btnSend"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@android:drawable/ic_media_play"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Отправить"
            android:layout_marginStart="8dp" />

        <ImageButton
            android:id="@+id/btnClose"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@android:drawable/ic_menu_close_clear_cancel"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Закрыть"
            android:layout_marginStart="4dp" />

    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/layout/input_window.xml
git commit -m "feat: replace templates strip with + (add file) button in layout"
```

---

## Task 7: FloatingService — подключить «＋», выпилить шаблоны

**Files:**
- Modify: `app/src/main/java/com/denis/floatinginput/FloatingService.kt`

> Удаляем мёртвый код шаблонов и заводим кнопку «＋». Это объёмная правка одного файла — выполнять по шагам и компилировать после.

- [ ] **Step 1: Заменить блок настройки кнопок в showInputWindow**

В `showInputWindow()` найти блок от объявления `val templatesScroll = …` до закрывающей `}` обработчика `btnToggle.setOnClickListener { … }` (строки, где берутся `templatesScroll`, `templatesContainer`, `btnToggle`, вызывается `setupTemplates`, выставляется `templatesVisible` и навешивается тоггл) и заменить его на получение и обработчик кнопки «＋»:

Заменить этот фрагмент:

```kotlin
        val btnSend = inputView!!.findViewById<ImageButton>(R.id.btnSend)
        val btnClose = inputView!!.findViewById<ImageButton>(R.id.btnClose)
        val templatesScroll = inputView!!.findViewById<HorizontalScrollView>(R.id.templatesScroll)
        val templatesContainer = inputView!!.findViewById<LinearLayout>(R.id.templatesContainer)
        val btnToggle = inputView!!.findViewById<ImageButton>(R.id.btnToggleTemplates)

        setupTemplates(templatesContainer, editText)
        templatesVisible = false

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
```

на:

```kotlin
        val btnSend = inputView!!.findViewById<ImageButton>(R.id.btnSend)
        val btnClose = inputView!!.findViewById<ImageButton>(R.id.btnClose)
        val btnAddFile = inputView!!.findViewById<ImageButton>(R.id.btnAddFile)

        // «＋» — выбрать файл и залить домой (через прозрачную activity-прокси)
        btnAddFile.setOnClickListener {
            val intent = Intent(this, FileUploadActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
```

- [ ] **Step 2: Удалить код и состояние шаблонов**

Удалить целиком следующие члены класса (объявления и методы):
- поля `private var templates = mutableListOf<String>()` и `private var templatesVisible = false`
- в `onCreate()` строку `templates = loadTemplates()`
- методы: `loadTemplates()`, `saveTemplates()`, `setupTemplates(...)`, `showTemplateMenu(...)`, `showTemplateEditDialog(...)`
- в `companion object` — `KEY_TEMPLATES` и `DEFAULT_TEMPLATES` (оставить `PREFS_NAME` — он больше нигде не используется после удаления шаблонов, тоже удалить)

> После удаления `loadTemplates`/`saveTemplates` константы `PREFS_NAME` и `KEY_TEMPLATES` становятся неиспользуемыми — удалить обе.

- [ ] **Step 3: Подчистить неиспользуемые импорты**

Удалить ставшие лишними импорты в начале файла (если их больше ничто не использует): `android.widget.FrameLayout`, `android.widget.HorizontalScrollView`, `android.widget.LinearLayout`, `org.json.JSONArray`. Оставить `android.widget.Button` только если он ещё используется (после удаления `setupTemplates` — проверить; виджет `btnEdit` это `Button` из floating_widget, берётся как `findViewById<Button>(R.id.btnEdit)` в createWidget — значит `Button` оставить).

> Проверка: `LinearLayout` — после удаления шаблонов не используется в коде (только в XML) → удалить импорт. `FrameLayout`, `HorizontalScrollView`, `JSONArray` → удалить. `Button` → оставить.

- [ ] **Step 4: Скомпилировать**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, без warning о неиспользуемых символах шаблонов. Если компилятор ругается на оставшуюся ссылку (`setupTemplates`/`templates`) — значит блок пропущен, найти и удалить.

- [ ] **Step 5: Прогнать юнит-тесты (регрессия)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (UploadNamingTest зелёный).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/denis/floatinginput/FloatingService.kt
git commit -m "feat: wire + button to FileUploadActivity, remove templates code"
```

---

## Task 8: Сборка, ручная проверка, выкладка APK

**Files:** нет (сборка/деплой).

- [ ] **Step 1: Проверить креды в UploadConfig**

Креды уже зашиты при реализации Task 3 (`sshhallwss` / `Kashatka123.` / `workshop/claude-inbox`) и проверены сквозным тестом в Task 1. Убедиться, что плейсхолдеров не осталось:

```bash
grep -n "ЗАПОЛНИТЬ" app/src/main/java/com/denis/floatinginput/UploadConfig.kt || echo "ok, плейсхолдеров нет"
```

Expected: `ok, плейсхолдеров нет`.

- [ ] **Step 2: Собрать debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL; файл `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Установить на телефон**

Debug-подпись совпадает с прошлой — встанет поверх 1.1 без удаления. Установить (через filebrowser-выкладку ниже + ручная установка, либо `adb install -r`, если телефон подключён).

- [ ] **Step 4: Ручная проверка (Денис)**

1. Открыть оверлей (✎), тапнуть «＋».
2. Системный пикер → выбрать фото.
3. Дождаться тоста «Путь скопирован в буфер» (при ошибке — тост с причиной).
4. В поле ввода (или в чате Termux) длинным тапом вставить — должен появиться путь `/home/denis/truenas/workshop/claude-inbox/ГГГГММДД-ЧЧММСС-<имя>`.
5. На домашней стороне: `ls -la /home/denis/truenas/workshop/claude-inbox/` — файл на месте; Claude делает `Read` по пути и видит содержимое.

> Проверять **с рабочей сети** (где живёт hallwss) — это целевой сценарий. Дома фильтра нет, но проверка корректности загрузки сработает в любой сети.

- [ ] **Step 5: Выложить APK в filebrowser**

Скопировать `app/build/outputs/apk/debug/app-debug.apk` в шару workshop как `workshop/apk/floating-input-1.2.apk` (как делалось для 1.1).

- [ ] **Step 6: Финальный commit (если были правки на шаге 4)**

```bash
git add -A && git commit -m "FloatingInput 1.2: + button file upload" || echo "нечего коммитить"
```

---

## Self-Review (выполнено при написании плана)

- **Покрытие спеки:** UI-кнопка «＋» (Task 6/7) ✓; пикер `ACTION_OPEN_DOCUMENT` `*/*` (Task 5) ✓; детерминированное имя + очистка (Task 3) ✓; FileUploader login→POST с `X-Auth` (Task 4) ✓; путь в буфер при успехе, тост-ошибка без записи в буфер (Task 4/5) ✓; конфиг в одном `UploadConfig` (Task 3) ✓; удаление кода шаблонов (Task 7) ✓; потоковая заливка из `InputStream` (Task 4, `setChunkedStreamingMode` + `copyTo`) ✓; юнит-тесты имени и пути (Task 3) ✓; инфра-подготовка (Task 1) ✓; сборка/выкладка с ростом версии (Task 8) ✓; YAGNI-исключения (SSH/scp/прогресс/ретраи) не реализуются ✓.
- **Плейсхолдеры:** единственный «плейсхолдер» — значение пароля `UploadConfig.PASSWORD`, это данные из Task 1, а не пробел в плане; явно закрывается в Task 8 Step 1.
- **Согласованность типов:** `FileUploader.Result(ok, homePath, error)` создаётся в Task 4 и потребляется в Task 5 одинаково; `UploadNaming.sanitize/uniqueName/remotePath` и `UploadConfig.*` имена совпадают между Task 3, 4; id `btnAddFile` совпадает в layout (Task 6) и FloatingService (Task 7).
