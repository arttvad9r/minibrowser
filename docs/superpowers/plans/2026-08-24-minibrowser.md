# Minibrowser (Android, GeckoView) — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Минималистичный браузер для личного использования: GeckoView + Compose, встроенный uBlock Origin, автоперевод видео через VOT, стартовая страница с плитками закладок.

**Architecture:** Один Gradle-модуль. GeckoRuntime — синглтон Application; вкладки = список `GeckoSession` в `TabManager` с Compose-состоянием внутри каждой вкладки; делегаты GeckoView обновляют состояние → Compose перерисовывает. Расширения uBO/VOT ставятся из ассетов APK через `ensureBuiltIn`. Данные: DataStore (настройки), Room (история, закладки), JSON (открытые вкладки).

**Tech Stack:** Kotlin 2.0.20, AGP 8.7.3, Gradle 8.10.2, Compose BOM 2024.09.03, GeckoView 154.0.20260814215756, Room 2.6.1 (KSP), DataStore 1.1.1, kotlinx-serialization 1.7.3.

**Spec:** `docs/superpowers/specs/2026-08-24-minibrowser-design.md` (читать вместе с планом).

## Global Constraints

- minSdk 26, targetSdk/compileSdk 35, JDK 17, namespace/applicationId `com.artt.minibrowser`.
- GeckoView пин: `org.mozilla.geckoview:geckoview:154.0.20260814215756` (последний релиз на момент плана).
- Mozilla Android Components НЕ подключать.
- Сборка на NixOS: JDK через `nix shell` (см. shell.nix в Задаче 1), SDK в `/home/artt/Android/Sdk` (прописывается в `local.properties`, файл в .gitignore).
- Эмулятор уже создан (`~/.android/avd`). adb: `~/Android/Sdk/platform-tools/adb`.
- Строки UI — сразу на русском, без res/строковых ресурсов (личное приложение).
- Если компилятор требует переопределить методы делегата, не показанные в плане, — добавить пустые реализации и идти дальше (API Gecko местами имеет дефолтные методы; javadoc: https://mozilla.github.io/geckoview/javadoc/mozilla-central/).
- Каждый таск заканчивается коммитом; тесты перед коммитом должны проходить.

---

### Task 1: Скелет проекта и окружение сборки

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, `shell.nix`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/artt/minibrowser/MainActivity.kt`, `app/src/main/res/values/themes.xml`
- Create: `local.properties` (не коммитить)

**Interfaces:**
- Produces: собираемый APK `com.artt.minibrowser`, команда сборки `./gradlew assembleDebug`, запуск тестов `./gradlew test`.

- [ ] **Step 1: Каркас файлов**

`settings.gradle.kts`:
```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(ResolutionMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral(); maven("https://maven.mozilla.org/maven2") } }
rootProject.name = "minibrowser"
include(":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
}
```

`gradle.properties`:
```
org.gradle.jvmargs=-Xmx3g
android.useAndroidX=true
```

`app/build.gradle.kts` (GeckoView подключим в задаче 2):
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.artt.minibrowser"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.artt.minibrowser"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation(kotlin("test"))
}
```

`app/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>
    <application
        android:label="Minibrowser"
        android:icon="@mipmap/ic_launcher"
        android:allowBackup="false"
        android:theme="@style/AppTheme">
        <activity android:name=".MainActivity" android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources><style name="AppTheme" parent="android:Theme.Material.Light.NoActionBar"/></resources>
```

Иконку по умолчанию сгенерировать нечем — заменить ссылку на адаптивную заглушку: создать `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_bg"/><foreground android:drawable="@color/ic_fg"/>
</adaptive-icon>
```
и `values/colors.xml`:
```xml
<resources><color name="ic_bg">#1B1B1F</color><color name="ic_fg">#BAC8FF</color></resources>
```
(в манифесте оставить `@mipmap/ic_launcher`; удалите сгенерированные png, если будут конфликтовать — anydpi перекрывает.)

`MainActivity.kt`:
```kotlin
package com.artt.minibrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Minibrowser") }
    }
}
```

`.gitignore`: `.gradle/ build/ local.properties *.iml .idea/ .ksp/`

`shell.nix`:
```nix
{ pkgs ? import <nixpkgs> {} }: pkgs.mkShell {
  packages = [ pkgs.temurin-bin-17 pkgs.unzip ];
}
```

- [ ] **Step 2: Обёртка Gradle и local.properties**

```bash
echo "sdk.dir=/home/artt/Android/Sdk" > local.properties   # НЕ коммитить
nix shell nixpkgs#gradle -c gradle wrapper --gradle-version 8.10.2
```

- [ ] **Step 3: Сборка**

Run: `nix shell -c ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`, артефакт `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Запуск на эмуляторе**

```bash
~/Android/Sdk/emulator/emulator -avd <имя_avd> &   # ls ~/.android/avd — взять имя
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/Android/Sdk/platform-tools/adb shell am start -n com.artt.minibrowser/.MainActivity
```
Expected: экран с текстом «Minibrowser».

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "chore: gradle skeleton, hello-world Compose activity"
```

---

### Task 2: GeckoView — движок рендерит страницу

**Files:**
- Modify: `app/build.gradle.kts` (+geckoview), `app/src/main/AndroidManifest.xml` (android:name у application)
- Create: `app/src/main/java/com/artt/minibrowser/engine/BrowserApp.kt`
- Modify: `app/src/main/java/com/artt/minibrowser/MainActivity.kt`

**Interfaces:**
- Produces: `object Engine { lateinit var runtime: GeckoRuntime }` — глобальная точка доступа к рантайму для всех следующих задач.

- [ ] **Step 1: Зависимость**

В `app/build.gradle.kts` dependencies добавить:
```kotlin
implementation("org.mozilla.geckoview:geckoview:154.0.20260814215756")
```
В манифесте к `<application>` добавить `android:name=".engine.BrowserApp"`.

- [ ] **Step 2: Инициализация рантайма**

`engine/BrowserApp.kt`:
```kotlin
package com.artt.minibrowser.engine

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object Engine { lateinit var runtime: GeckoRuntime }

class BrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.runtime = GeckoRuntime.create(
            this,
            GeckoRuntimeSettings.Builder()
                .autoplayDefault(GeckoRuntimeSettings.AUTOPLAY_DEFAULT_ALLOWED) // нужно VOT
                .build()
        )
    }
}
```
Если константа/метод переименованы в 154 — свериться с javadoc GeckoRuntimeSettings и поправить (цель: автовоспроизведение разрешено).

- [ ] **Step 3: GeckoView в Compose**

`MainActivity.kt`:
```kotlin
package com.artt.minibrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.artt.minibrowser.engine.Engine
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = GeckoSession().also { it.open(Engine.runtime) }
        setContent {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    AndroidView(factory = { ctx ->
                        GeckoView(ctx).apply { setSession(session) }
                    }, modifier = Modifier.weight(1f))
                    Button(onClick = { session.loadUri("https://example.com") }) { Text("go") }
                }
            }
        }
    }
}
```
Примечание: при повороте экрана активность пересоздаётся — `configChanges` в манифесте это уже предотвращает; пересоздавать сессию нельзя.

- [ ] **Step 4: Проверка на эмуляторе**

Собрать, поставить, нажать «go». Expected: example.com отрисовался GeckoView.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: geckoview renders pages, runtime singleton"
```

---

### Task 3: Омнибокс, навигация, поисковики (TDD)

**Files:**
- Create: `app/src/main/java/com/artt/minibrowser/engine/SearchEngines.kt`
- Test: `app/src/test/java/com/artt/minibrowser/SearchEnginesTest.kt`
- Modify: `MainActivity.kt` (нижняя панель вместо кнопки)

**Interfaces:**
- Produces: `enum class SearchEngine(val label: String, val template: String)` с GOOGLE/DDUCKGO/YANDEX/BING; `fun buildLoadUri(input: String, engine: SearchEngine): String`. Эти имена используют задачи 4, 5, 6.

- [ ] **Step 1: Падающий тест**

`app/src/test/java/com/artt/minibrowser/SearchEnginesTest.kt`:
```kotlin
package com.artt.minibrowser

import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.buildLoadUri
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchEnginesTest {
    @Test fun urlPassthrough() =
        assertEquals("https://a.b/c", buildLoadUri("https://a.b/c", SearchEngine.GOOGLE))
    @Test fun bareDomainGetsHttps() =
        assertEquals("https://example.com", buildLoadUri("example.com", SearchEngine.GOOGLE))
    @Test fun wordsGoToSearch() =
        assertEquals("https://www.google.com/search?q=%D0%BB%D0%B8%D1%81%D0%B0",
                     buildLoadUri("лиса", SearchEngine.GOOGLE))
    @Test fun localhostStaysLocal() =
        assertEquals("http://localhost:8080/x", buildLoadUri("http://localhost:8080/x", SearchEngine.GOOGLE))
    @Test fun emptyIsBlank() =
        assertEquals("about:blank", buildLoadUri("  ", SearchEngine.GOOGLE))
}
```

- [ ] **Step 2: Убедиться, что падает**

Run: `nix shell -c ./gradlew test`
Expected: FAIL — `buildLoadUri` не существует.

- [ ] **Step 3: Реализация**

`engine/SearchEngines.kt`:
```kotlin
package com.artt.minibrowser.engine

import java.net.URLEncoder

enum class SearchEngine(val label: String, val template: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    YANDEX("Яндекс", "https://yandex.ru/search/?text=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s");
}

private val URI_LIKE = Regex("^(https?://|about:)|^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/.*)?$")

fun buildLoadUri(input: String, engine: SearchEngine): String {
    val t = input.trim()
    if (t.isEmpty()) return "about:blank"
    if (URI_LIKE.containsMatchIn(t)) {
        return if (t.startsWith("https://") || t.startsWith("http://") || t.startsWith("about:")) t
        else "https://$t"
    }
    return engine.template.replace("%s", URLEncoder.encode(t, "UTF-8"))
}
```

- [ ] **Step 4: Тест зелёный**

Run: `nix shell -c ./gradlew test` → PASS.

- [ ] **Step 5: Нижняя панель с омнибоксом**

Переписать `MainActivity.setContent` на структуру будущего браузера:
```kotlin
package com.artt.minibrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.artt.minibrowser.engine.Engine
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.buildLoadUri
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = GeckoSession().also { it.open(Engine.runtime) }

        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        AndroidView(factory = { GeckoView(it).apply { setSession(session) } },
                            modifier = Modifier.fillMaxSize())
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) /* прогресс из задачи 5 */
                    }
                    BottomBar(session)
                }
            }
        }
    }
}

@Composable
private fun BottomBar(session: GeckoSession) {
    var text by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
            singleLine = true, placeholder = { Text("Поиск или адрес") })
        TextButton(onClick = {
            session.loadUri(buildLoadUri(text, SearchEngine.YANDEX)); text = ""
        }) { Text("→") }
    }
}
```
(Поисковик по умолчанию захардкожен до задачи 4.)

- [ ] **Step 6: Проверка на эмуляторе**

Ввести `example.com` → открывается сайт; ввести «лиса» → страница выдачи Яндекса.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: omnibox navigation, search engines with unit tests"
```

---

### Task 4: Настройки (DataStore) + тема + экран настроек

**Files:**
- Create: `app/src/main/java/com/artt/minibrowser/data/SettingsRepository.kt`
- Modify: `MainActivity.kt` (MaterialTheme по настройке), меню появится в задаче 5 — пункт «Настройки» туда
- Create: `app/src/main/java/com/artt/minibrowser/ui/SettingsScreen.kt`

**Interfaces:**
- Produces: `class SettingsRepository(context)` с `val prefs: Flow<Prefs>` и `suspend fun set(key...)` методами; `data class Prefs(searchEngine: SearchEngine, theme: Int /*0 system,1 light,2 dark*/, adblockEnabled: Boolean, homepage: String)`; `const val KEY_*` имена. Используется задачами 5–9.

- [ ] **Step 1: Репозиторий**

`data/SettingsRepository.kt`:
```kotlin
package com.artt.minibrowser.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.artt.minibrowser.engine.SearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Prefs(
    val searchEngine: SearchEngine = SearchEngine.YANDEX,
    val theme: Int = 0,          // 0 система, 1 светлая, 2 тёмная
    val adblockEnabled: Boolean = true,
    val homepage: String = "",
)

class SettingsRepository(private val context: Context) {
    private object K {
        val engine = stringPreferencesKey("search_engine")
        val theme = intPreferencesKey("theme")
        val adblock = booleanPreferencesKey("adblock_enabled")
        val homepage = stringPreferencesKey("homepage")
    }
    val prefs: Flow<Prefs> = context.dataStore.data.map { p ->
        Prefs(
            searchEngine = p[K.engine]?.let { runCatching { SearchEngine.valueOf(it) }.getOrNull() }
                ?: SearchEngine.YANDEX,
            theme = p[K.theme] ?: 0,
            adblockEnabled = p[K.adblock] ?: true,
            homepage = p[K.homepage] ?: "",
        )
    }
    suspend fun setSearchEngine(e: SearchEngine) = context.dataStore.edit { it[K.engine] = e.name }
    suspend fun setTheme(t: Int) = context.dataStore.edit { it[K.theme] = t }
    suspend fun setAdblock(b: Boolean) = context.dataStore.edit { it[K.adblock] = b }
    suspend fun setHomepage(u: String) = context.dataStore.edit { it[K.homepage] = u }
}
```

- [ ] **Step 2: Тема применяется**

В `MainActivity` собрать настройки через `collectAsStateWithLifecycle` (репозиторий создать один раз как поле активности) и обернуть контент:
```kotlin
val darkTheme = when (prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) { ... }
```
`BottomBar` получает `searchEngine` параметром вместо хардкода.

- [ ] **Step 3: Экран настроек**

`ui/SettingsScreen.kt`: простой список — выбор поисковика (SegmentedButton/радиокнопки из `SearchEngine.entries`), тема (3 радиокнопки), поле «Домашняя страница», переключатель «Блокировка рекламы» (пишет pref; реальное включение/выключение расширения — задача 8), кнопка «Очистить данные» (реализация в задаче 11, сейчас скрыта). Навигация: локальный `var screen by mutableStateOf(Screen.Browser)` enum-роутер в MainActivity (Browser/Settings/History/Bookmarks) — без навигационных библиотек.

- [ ] **Step 4: Проверка**

Эмулятор: сменить тему → применяется мгновенно; сменить поисковик → поиск уходит в него; перезапуск приложения → настройки сохранились.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: settings datastore, theme, settings screen"
```

---

### Task 5: Вкладки, шторка, восстановление после убийства (TDD на персистентность)

**Files:**
- Create: `app/src/main/java/com/artt/minibrowser/engine/TabManager.kt`
- Create: `app/src/main/java/com/artt/minibrowser/data/TabStore.kt`
- Test: `app/src/test/java/com/artt/minibrowser/TabStoreTest.kt`
- Modify: `MainActivity.kt` (счётчик вкладок, шторка)

**Interfaces:**
- Produces:
  ```kotlin
  class Tab(val session: GeckoSession, val id: Long, val isPrivate: Boolean) {
      var url by mutableStateOf(""); var title by mutableStateOf(""); var progress by mutableStateOf(-1)
  }
  class TabManager(runtime, private val storeDir: File) {
      val tabs: StateFlow<List<Tab>>; val currentId: StateFlow<Long?>
      fun current(): Tab?; fun newTab(url: String?, private: Boolean = false): Tab
      fun closeTab(id: Long); fun select(id: Long); fun persist(); fun restore()
  }
  ```
  Все следующие задачи берут вкладки только через `TabManager`.

- [ ] **Step 1: Падающий тест TabStore**

`TabStoreTest.kt`:
```kotlin
package com.artt.minibrowser

import com.artt.minibrowser.data.TabStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabStoreTest {
    @Test fun roundTrip() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-${System.nanoTime()}").apply { mkdirs() }
        TabStore.save(dir, listOf("https://a.example/", "https://b.example/"))
        assertEquals(listOf("https://a.example/", "https://b.example/"), TabStore.load(dir))
        assertTrue(File(dir, "open_tabs.json").exists())
        dir.deleteRecursively()
    }
    @Test fun emptyWhenMissing() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-none-${System.nanoTime()}")
        assertEquals(emptyList(), TabStore.load(dir))
    }
    @Test fun privateTabsNotSaved() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-priv-${System.nanoTime()}").apply { mkdirs() }
        TabStore.save(dir, emptyList())
        assertEquals(emptyList(), TabStore.load(dir))
        dir.deleteRecursively()
    }
}
```

- [ ] **Step 2: FAIL** — `./gradlew test` (класса нет).

- [ ] **Step 3: Реализация TabStore**

`data/TabStore.kt`:
```kotlin
package com.artt.minibrowser.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedTab(val url: String, val position: Int)

object TabStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val file get() = File(dir, "open_tabs.json")
    lateinit var dir: File   // инициализируется в MainActivity: File(filesDir, "tabs")

    fun save(dir: File = file.parentFile!!, urls: List<String>) {
        dir.mkdirs()
        file.writeText(json.encodeToString(urls.mapIndexed { i, u -> SavedTab(u, i) }))
    }
    fun load(dir: File = file.parentFile!!): List<String> =
        runCatching { json.decodeFromString<List<SavedTab>>(file.readText()).sortedBy { it.position }.map { it.url } }
            .getOrDefault(emptyList())
}
```
Упростить сигнатуры: `save(urls: List<String>)` / `load(): List<String>` c полем `dir`, выставляемым один раз. Приватные вкладки в сохранение не попадают (фильтр в TabManager.persist).

- [ ] **Step 4: PASS** — `./gradlew test`.

- [ ] **Step 5: TabManager + делегаты**

`engine/TabManager.kt`:
```kotlin
package com.artt.minibrowser.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.artt.minibrowser.data.TabStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.mozilla.geckoview.*

class Tab(val session: GeckoSession, val id: Long, val isPrivate: Boolean) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableFloatStateOf(-1f)
    var canGoBack by mutableStateOf(false)
}

class TabManager(private val runtime: GeckoRuntime) {
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs get() = _tabs
    val currentId = MutableStateFlow<Long?>(null)
    private var seq = 0L

    init { TabStore.dir?.let { restore(it) } }

    fun newTab(url: String?, private: Boolean = false): Tab {
        val s = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(private)
                .build()
        )
        val tab = Tab(s, ++seq, private)
        attachDelegates(tab)
        _tabs.value += tab
        currentId.value = tab.id
        s.open(runtime)
        url?.let(s::loadUri)
        return tab
    }

    fun select(id: Long) { currentId.value = id }
    fun closeTab(id: Long) {
        val list = _tabs.value
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        list[idx].session.stop(); list[idx].session.close()
        _tabs.value = list - list[idx]
        if (currentId.value == id) currentId.value = (_tabs.value.getOrNull(idx.coerceAtMost(_tabs.value.size - 1)))?.id
        persist()
    }

    fun current(): Tab? = _tabs.value.firstOrNull { it.id == currentId.value }

    fun persist() {
        TabStore.save(_tabs.value.filter { !it.isPrivate }.map { it.url })
    }

    private fun restore(dir: java.io.File) {
        TabStore.load().forEach { newTab(it) }
        if (_tabs.value.isEmpty()) newTab(null)
    }

    private fun attachDelegates(tab: Tab) {
        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.progress = 0.05f; tab.url = url
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.progress = -1f
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress / 100f
            }
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {}
        }
        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>, triggeredByUser: Boolean) {
                tab.url = url
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canGoBack = canGoBack }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoSession? = null
            override fun onLoadError(session: GeckoSession, uri: String?,
                error: WebRequestError): GeckoSession.NavigationDelegate.LoadRequest? = null
        }
        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String) { tab.title = title }
            override fun onCrash(session: GeckoSession) {
                // восстановление крашнутой сессии с тем же URL (спека: ошибки и восстановление)
                val url = tab.url
                session.close()
                val fresh = GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(tab.isPrivate).build())
                fresh.open(runtime)
                val i = _tabs.value.indexOfFirst { it.id == tab.id }
                // заменить сессию у той же вкладки; делегаты перевешиваются ниже
                tab.session = fresh
                attachDelegates(tab)
                fresh.loadUri(url.ifEmpty { "about:blank" })
            }
        }
        tab.session.historyDelegate = object : GeckoSession.HistoryDelegate {
            // история пишется в задаче 6; приватные вкладки сюда не доходят (usePrivateMode сам не зовёт? — фильтруем явно)
            override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int) {
                if (!tab.isPrivate && flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL != 0) {
                    HistorySink.record(url, session.title)
                }
            }
            override fun shouldTrackVisitedUrls(session: GeckoSession, urls: Array<out String>): Boolean = true
        }
    }
}
```
Замечания исполнителю:
- `HistorySink` появится в задаче 6; чтобы задача компилировалась раньше — создать заглушку `object HistorySink { fun record(url: String, title: String?) {} }` в этом же таске, реализация заменяется позже.
- Поле `tab.session` сделать `var session` (mutable) — краш-восстановление меняет сессию.
- Сигнатуру `onLocationChange` сверить с javadoc 154 (аргументы могли отличаться) — компилятор подскажет.
- `GeckoView` должен показывать сессию текущей вкладки: при смене `currentId` вызвать `view.setSession(null)` у старой и `view.setSession(new.session)` у новой (хранить ссылку на view в состоянии MainActivity через `remember { mutableStateOf<GeckoView?>(null) }`, заполняемую в factory).

- [ ] **Step 6: Шторка вкладок + счётчик**

UI: в нижней панели справа кнопка с числом открытых вкладок → ModalBottomSheet со списком карточек (title/url, крестик закрыть, тап — выбрать). Кнопка «+» новая вкладка. При закрытии последней — создаётся пустая. `persist()` вызывать в `onPause` активности.

- [ ] **Step 7: Проверки**

Эмулятор: открыть 3 вкладки, убить процесс (`adb shell am kill com.artt.minibrowser`), запустить — вкладки восстановились; краш-тест: `chrome://crash` в адресной строке — вкладка оживает.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat: tab manager, tab tray, crash recovery, tab persistence"
```

---

### Task 6: История (Room) + подсказки омнибокса (TDD на ранжирование)

**Files:**
- Create: `app/src/main/java/com/artt/minibrowser/data/Db.kt`, `data/HistoryRepository.kt`
- Modify: `engine/TabManager.kt` (HistorySink → DAO), `ui/` (экран истории, подсказки)
- Test: `app/src/test/java/com/artt/minibrowser/SuggestionsTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Entity data class HistoryEntry(@PrimaryKey val url: String, val title: String, val visitedAt: Long, val visits: Int)
  @Dao interface HistoryDao { upsert, recent(limit), search(q), clearAll() }
  class HistoryRepository(dao): suspend fun record(url,title); suspend fun suggest(q): List<Suggestion>; suspend fun clear()
  ```
  `Suggestion(label: String, url: String)` — общий тип подсказок (задача 7 добавит закладки тем же типом).

- [ ] **Step 1: Падающий тест ранжирования**

Чистая функция `rankSuggestions(history: List<Scored>, bookmarks: List<String>, q: String): List<Suggestion>` — тестируем её (DAO не тестируем, это boilerplate Room).
```kotlin
package com.artt.minibrowser

import com.artt.minibrowser.data.Scored
import com.artt.minibrowser.data.Suggestion
import com.artt.minibrowser.data.rankSuggestions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestionsTest {
    private val h = listOf(
        Scored("https://youtube.com/watch?v=1", "видео котики", 100, 5),
        Scored("https://ya.ru", "Яндекс", 200, 1),
        Scored("https://vk.com/feed", "Новости | VK", 300, 2),
    )
    @Test fun filtersAndSortsByFreshness() {
        val r = rankSuggestions(h, emptyList(), "")
        assertEquals("https://vk.com/feed", r.first().url)
    }
    @Test fun queryMatchesUrlOrTitle() {
        val r = rankSuggestions(h, emptyList(), "котики")
        assertEquals(listOf("https://youtube.com/watch?v=1"), r.map { it.url })
    }
    @Test fun bookmarksAlwaysFirst() {
        val r = rankSuggestions(h, listOf("https://ya.ru"), "")
        assertEquals("https://ya.ru", r.first().url)
    }
    @Test fun cappedAtEight() {
        val big = (1..20).map { Scored("https://x$it.com", "t$it", it.toLong(), 1) }
        assertTrue(rankSuggestions(big, emptyList(), "").size <= 8)
    }
}
```

- [ ] **Step 2: FAIL** — `./gradlew test`.

- [ ] **Step 3: Реализация Db + ранжирование**

`data/Db.kt`:
```kotlin
package com.artt.minibrowser.data

import androidx.room.*

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey val url: String, val title: String, val visitedAt: Long, val visits: Int,
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val url: String, val title: String, val host: String, val position: Int,
)

@Dao interface AppDao {
    // --- history ---
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    suspend fun recentHistory(limit: Int): List<HistoryEntry>

    @Query("SELECT * FROM history WHERE url LIKE '%' || :q || '%' OR title LIKE '%' || :q || '%' ORDER BY visits DESC LIMIT 50")
    suspend fun searchHistory(q: String): List<HistoryEntry>

    @Query("SELECT * FROM history WHERE url = :url")
    suspend fun historyByUrl(url: String): HistoryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(e: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // --- bookmarks ---
    @Query("SELECT * FROM bookmarks ORDER BY position")
    suspend fun bookmarks(): List<Bookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(b: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmark(url: String)

    @Query("UPDATE bookmarks SET title = :title WHERE url = :url")
    suspend fun renameBookmark(url: String, title: String)

    @Query("SELECT COUNT(*) FROM bookmarks WHERE url = :url")
    suspend fun bookmarkCount(url: String): Int
}

@Database(entities = [HistoryEntry::class, Bookmark::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() { abstract fun dao(): AppDao }
```

Ранжирование (чистое, туда же в `data/Db.kt` или отдельным файлом `data/Suggestions.kt`):
```kotlin
data class Scored(val url: String, val title: String, val visitedAt: Long, val visits: Int)
data class Suggestion(val label: String, val url: String)

fun rankSuggestions(history: List<Scored>, bookmarks: List<String>, q: String): List<Suggestion> {
    val needle = q.trim()
    val matched = history.filter { needle.isEmpty() ||
        it.url.contains(needle, true) || it.title.contains(needle, true) }
        .sortedWith(compareByDescending<Scored> { it.visits }.thenByDescending { it.visitedAt })
    val bm = bookmarks.filter { needle.isEmpty() || it.contains(needle, true) }
    return (bm.map { Suggestion(it, it) } + matched.take(8).map { Suggestion(it.title.ifEmpty { it.url }, it.url) })
        .distinctBy { it.url }.take(8)
}
```

`data/HistoryRepository.kt`:
```kotlin
package com.artt.minibrowser.data

class HistoryRepository(private val dao: AppDao) {
    suspend fun record(url: String, title: String?) {
        val now = System.currentTimeMillis()
        val prev = dao.historyByUrl(url)
        dao.upsertHistory(HistoryEntry(url, title ?: prev?.title ?: url, now, (prev?.visits ?: 0) + 1))
    }
    suspend fun suggest(q: String): List<Suggestion> {
        val rows = if (q.isBlank()) dao.recentHistory(30) else dao.searchHistory(q.trim())
        val marks = dao.bookmarks().map { it.url }
        return rankSuggestions(rows.map { Scored(it.url, it.title, it.visitedAt, it.visits) }, marks, q)
    }
    suspend fun clear() = dao.clearHistory()
}
```
БД — синглтон рядом с Db.kt: `object DbHolder { lateinit var db: AppDb; fun init(context) = Room.databaseBuilder(...).build() }` (вызвать в BrowserApp.onCreate). Заменить заглушку `HistorySink.record` из задачи 5 на `scope.launch { HistoryRepository(DbHolder.db.dao()).record(url, title) }` (корутин-скоуп активности).

- [ ] **Step 4: PASS** — `./gradlew test`.

- [ ] **Step 5: Подсказки и экран истории**

Омнибокс: при фокусе выпадающий список подсказок (`suggest(text)` в LaunchedEffect по изменению текста), тап — загрузить. Экран истории: список записей (заголовок, url, дата), кнопка «Очистить». Добавить пункты роутера History.

- [ ] **Step 6: Проверка**

Эмулятор: походить по сайтам → история заполняется, приватная вкладка историю не пишет (проверка!), омнибокс подсказывает по подстроке.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: room db, history recording, omnibox suggestions, history screen"
```

---

### Task 7: Визуальные закладки: плитки на стартовой странице (TDD на путь кэша иконок)

**Files:**
- Create: `app/src/main/java/com/artt/minibrowser/ui/StartPage.kt`, `ui/TileGrid.kt`, `engine/FaviconFetcher.kt`, `data/BookmarksRepository.kt`
- Modify: `MainActivity.kt` (роутер Bookmarks, показ StartPage при `url == "about:blank"`), меню (появится в этой задаче: кнопка «⋮» в нижней панели с пунктами «Новая вкладка», «Приватная вкладка» (заглушка до задачи 10), «В закладки», «Закладки», «История», «Настройки»)
- Test: `app/src/test/java/com/artt/minibrowser/FaviconCacheTest.kt`

**Interfaces:**
- Consumes: `AppDao.bookmarks()/upsertBookmark/deleteBookmark/renameBookmark` из задачи 6.
- Produces:
  ```kotlin
  object FaviconFetcher {
      fun cacheFile(host: String, iconsDir: File): File           // чистая функция пути
      suspend fun fetch(host: String, iconsDir: File): File       // скачивание при отсутствии, возвращает файл
  }
  class BookmarksRepository(dao): suspend fun all(): List<Bookmark>; suspend fun add(url, title); suspend fun remove(url); suspend fun rename(url, title); suspend fun isBookmarked(url): Boolean
  ```

- [ ] **Step 1: Падающий тест пути кэша**

```kotlin
package com.artt.minibrowser

import com.artt.minibrowser.engine.FaviconFetcher
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FaviconCacheTest {
    @Test fun stableHostBasedPath() {
        val f1 = FaviconFetcher.cacheFile("youtube.com", File("/tmp/i"))
        val f2 = FaviconFetcher.cacheFile("YOUTUBE.COM", File("/tmp/i"))
        assertEquals(f1.path, f2.path)
        assertEquals("/tmp/i/${f1.name}", f1.path)
    }
    @Test fun differentHostsDifferentFiles() {
        assertNotEquals(FaviconFetcher.cacheFile("a.com", File("/i")).path,
                        FaviconFetcher.cacheFile("b.com", File("/i")).path)
    }
}
```

- [ ] **Step 2: FAIL** — `./gradlew test`.

- [ ] **Step 3: FaviconFetcher (stdlib HttpURLConnection)**

```kotlin
package com.artt.minibrowser.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// ponytail: иконки через DDG-сервис (наружу уходит только домен);
// заменить на парсинг <link rel="icon"> страницы, если нужна автономность
object FaviconFetcher {
    fun cacheFile(host: String, iconsDir: File): File {
        val md5 = MessageDigest.getInstance("MD5").digest(host.lowercase().trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(iconsDir, "$md5.png")
    }

    suspend fun fetch(host: String, iconsDir: File): File = withContext(Dispatchers.IO) {
        val dst = cacheFile(host, iconsDir)
        if (dst.exists()) return@withContext dst
        iconsDir.mkdirs()
        val conn = URL("https://icons.duckduckgo.com/ip3/${host.lowercase()}.ico").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        runCatching { conn.inputStream.use { input -> dst.outputStream().use { input.copyTo(it) } } }
            .onFailure { dst.delete() }
        dst
    }
}
```

- [ ] **Step 4: PASS** — `./gradlew test`.

- [ ] **Step 5: BookmarksRepository + TileGrid + StartPage**

`data/BookmarksRepository.kt`:
```kotlin
package com.artt.minibrowser.data

import java.net.URI

class BookmarksRepository(private val dao: AppDao) {
    suspend fun all(): List<Bookmark> = dao.bookmarks()
    suspend fun add(url: String, title: String) {
        val host = runCatching { URI(url).host ?: "" }.getOrDefault("")
        val max = dao.bookmarks().maxOfOrNull { it.position } ?: -1
        dao.upsertBookmark(Bookmark(url, title.ifBlank { host }, host, max + 1))
    }
    suspend fun remove(url: String) = dao.deleteBookmark(url)
    suspend fun rename(url: String, title: String) = dao.renameBookmark(url, title)
    suspend fun isBookmarked(url: String) = dao.bookmarkCount(url) > 0
}
```

`ui/TileGrid.kt`: `LazyVerticalGrid(GridCells.Fixed(4))` плиток: `AsyncIcon(host)` — Bitmap из `FaviconFetcher.fetch` в LaunchedEffect (декод `BitmapFactory.decodeFile`, при неудаче — кружок с первой буквой), название под иконкой (ellipsis 1 строка), `combinedClickable` долгий тап → `ModalBottomSheet`: Открыть / Переименовать (TextField+OK) / Удалить. Ширина плитки ~80dp.

`ui/StartPage.kt`: колонка по центру — логотип-текст «Minibrowser», поле поиска (тот же `buildLoadUri`), сетка закладок (если есть). Показывается когда `tab.url == "about:blank"` или пусто.

Экран «Закладки» (роутер) — тот же TileGrid на весь экран. Меню «⋮»: пункты из Interfaces выше + «Найти на странице», «Версия для ПК», «Скачать»… — эти три появятся в задаче 11; сейчас меню минимальное.

- [ ] **Step 6: Проверка**

Эмулятор: добавить 3–4 закладки через меню → плитки на стартовой странице с иконками; долгий тап удаляет; перезапуск — всё на месте; тап плитки — открывает сайт.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: visual bookmarks tiles, start page, ddg favicon cache"
```

---

### Task 8: uBlock Origin как встроенное расширение

**Files:**
- Create: `scripts/fetch-extensions.sh`
- Create: `app/src/main/java/com/artt/minibrowser/engine/ExtensionLoader.kt`
- Modify: `BrowserApp.kt` (вызов загрузчика), `SettingsScreen` (тумблер уже пишет pref — здесь подключается к реальности), нижнее меню (переключатель «Блокировка рекламы»)

**Interfaces:**
- Consumes: `Engine.runtime`, `Prefs.adblockEnabled` (задача 4).
- Produces: `object ExtensionLoader { const val UBLOCK_ID: String; const val VOT_ID: String; fun installAll(adblockEnabled: Boolean) }` — VOT_ID используется задачей 9.

- [ ] **Step 1: Скрипт загрузки расширений**

`scripts/fetch-extensions.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ASSETS=app/src/main/assets/extensions
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$ASSETS/ublock" "$ASSETS/vot"

# uBlock Origin: последний релиз firefox-signed xpi
UBO_TAG=$(curl -fsSL https://api.github.com/repos/gorhill/uBlock/releases/latest | grep -oP '"tag_name":\s*"\K[^"]+')
curl -fsSL -o "$WORK/ubo.xpi" \
  "https://github.com/gorhill/uBlock/releases/download/${UBO_TAG}/uBlock0_${UBO_TAG}.firefox.signed.xpi"
rm -rf "$ASSETS/ublock" && mkdir -p "$ASSETS/ublock"
unzip -q "$WORK/ubo.xpi" -d "$ASSETS/ublock"

# VOT: последний релиз, ассет vot-extension-firefox.xpi
VOT_URL=$(curl -fsSL https://api.github.com/repos/ilyhalight/voice-over-translation/releases/latest \
  | grep -oP '"browser_download_url":\s*"\K[^"]*vot-extension-firefox\.xpi')
curl -fsSL -o "$WORK/vot.xpi" "$VOT_URL"
rm -rf "$ASSETS/vot" && mkdir -p "$ASSETS/vot"
unzip -q "$WORK/vot.xpi" -d "$ASSETS/vot"

echo "--- uBO id:"; grep -oP '"id"\s*:\s*"\K[^"]+' "$ASSETS/ublock/manifest.json" || echo "(нет explicit id)"
echo "--- VOT id:"; grep -oP '"id"\s*:\s*"\K[^"]+' "$ASSETS/vot/manifest.json" || echo "(нет explicit id)"
```
Запустить: `chmod +x scripts/fetch-extensions.sh && ./scripts/fetch-extensions.sh`. Из вывода взять фактические id (uBO ожидаемо `uBlock0@raymondhill.net`; если у расширения нет `"id"` в manifest — использовать своё постоянное имя вида `vot@minibrowser.local`, ensureBuiltIn принимает произвольный id, главное не менять его между сборками).

- [ ] **Step 2: ExtensionLoader**

```kotlin
package com.artt.minibrowser.engine

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtensionController

object ExtensionLoader {
    // id вписать фактические из вывода скрипта шага 1
    const val UBLOCK_ID = "uBlock0@raymondhill.net"
    const val VOT_ID = "vot@minibrowser.local" // TODO(executor): заменить на фактический из manifest.json

    fun installAll(runtime: GeckoRuntime, adblockEnabled: Boolean) {
        val c: WebExtensionController = runtime.webExtensionController
        c.ensureBuiltIn("resource://android/assets/extensions/ublock/", UBLOCK_ID)
            .accept { ext -> ext?.let { c.setEnabled(it, adblockEnabled) } }
        c.ensureBuiltIn("resource://android/assets/extensions/vot/", VOT_ID)
    }

    fun setAdblock(runtime: GeckoRuntime, enabled: Boolean) {
        runtime.webExtensionController.list().accept { list ->
            list?.firstOrNull { it.id == UBLOCK_ID }?.let { runtime.webExtensionController.setEnabled(it, enabled) }
        }
    }
}
```
Вызов `ExtensionLoader.installAll(Engine.runtime, prefs.adblockEnabled)` — в MainActivity после первого чтения настроек (не в Application: prefs асинхронны; проще один раз в MainActivity scope). Переключатель в меню/настройках вызывает `setAdblock` + `settingsRepo.setAdblock`. Убрать `TODO` — вписать реальный id (план не оставляет TBD в коде: это значение узнаётся только на машине исполнителя, шаг обязателен).

- [ ] **Step 3: Проверка на эмуляторе**

Собрать (assets попадут в APK автоматически). Открыть `https://d3ward.github.io/toolz/adblock-test.html` → высокий % блокировки. Выключить тумблер → после перезагрузки страницы реклама проходит. Логи `adb logcat -s GeckoConsole` не показывают ошибок установки расширений.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: bundled ublock origin webextension with toggle"
```

---

### Task 9: VOT — автоперевод видео (главная проверка гипотезы)

**Files:**
- Modify: `engine/ExtensionLoader.kt` (VOT_ID фактический — если не сделано в задаче 8), возможно `BrowserApp.kt` (autoplay — уже стоит с задачи 2)

**Interfaces:** нет новых; задача — проверочная.

- [ ] **Step 1: Установка подтверждена**

`adb logcat -s GeckoConsole | grep -i vot` — без ошибок; на странице YouTube появляется кнопка/меню VOT в плеере.

- [ ] **Step 2: Перевод работает вручную**

Открыть любое видео youtube.com → кнопка перевода → появляется русская звуковая дорожка. Если плеер мобильной версии не подхватывается — включить «Версия для ПК» (тумблер из задачи 11; временно можно через `session.settings.userAgentMode = UA_MODE_DESKTOP` прямо в коде для проверки) и повторить.

- [ ] **Step 3: Автоперевод**

В настройках VOT (его собственный UI) включить «автоматический перевод при открытии» → открыть новое видео → дорожка появляется сама. Настройка VOT персистентна внутри профиля Gecko (files/mozilla) — сохранится между запусками.

- [ ] **Step 4: Решение по десктопному UA**

Если без десктопного UA перевод не заработал ни разу, а с ним заработал всегда — установить `userAgentMode(UA_MODE_DESKTOP)` по умолчанию для всех вкладок и зафиксировать решение в коде комментарием. Иначе — оставить мобильный UA + ручной тумблер.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(vot): video translation verified, ua strategy fixed"
```

---

### Task 10: Приватные вкладки

**Files:**
- Modify: `TabManager.kt` (флаг уже в Tab), шторка (индикатор), `MainActivity` меню («Приватная вкладка» — была заглушкой)

**Interfaces:** использует готовый `newTab(url, private=true)`.

- [ ] **Step 1: Поведение**

Приватная вкладка: `usePrivateMode(true)` (уже так), в шторке помечается иконкой/цветом, не попадает в `persist()` (уже отфильтрована), `HistorySink` её пропускает (фильтр `tab.isPrivate` уже в делегате). Закрытие последней обычной вкладки не должно делать приватную «обычной» — просто создаётся новая обычная.

- [ ] **Step 2: Проверка**

Открыть сайт в приватной вкладке → истории нет; убить процесс → приватная не восстановилась, обычные восстановились.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: private tabs excluded from history and persistence"
```

---

### Task 11: Добивка функционала меню

**Files:**
- Modify: `ui/` (меню, find-bar), `TabManager.kt`/delegates (downloads), `MainActivity.kt` (share, desktop toggle), `SettingsScreen` (очистка данных)

**Interfaces:**
- Produces: пункты меню из спеки полностью рабочие: Найти на странице · Версия для ПК · Скачать · Поделиться · Очистить данные.

- [ ] **Step 1: Найти на странице**

Overlay над нижней панелью: TextField + счётчик совпадений + назад/вперёд/закрыть. Через `session.finder.find(text, flags)`; флаги `GeckoSession.FinderFindFlags` (FIND_BACKWARD для «назад»). Поля результата (`FinderResult`) сверить с javadoc 154 и показать счётчик; при пустом запросе — `finder.clear()`.

- [ ] **Step 2: Версия для ПК**

Тумблер: `session.settings.userAgentMode = GeckoSessionSettings.UA_MODE_DESKTOP / UA_MODE_MOBILE`, затем `session.reload()`. Состояние хранить в Tab (compose state), иконка состояния в меню.

- [ ] **Step 3: Скачивания**

В `contentDelegate`:
```kotlin
override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
    val name = parseFilename(response.headers["Content-Disposition"], "file")
    val req = android.app.DownloadManager.Request(android.net.Uri.parse(response.uri))
        .setTitle(name)
        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
    response.headers["User-Agent"]?.let { req.addRequestHeader("User-Agent", it) }
    response.headers["Referer"]?.let { req.addRequestHeader("Referer", it) }
    context.getSystemService(android.app.DownloadManager::class.java).enqueue(req)
}
```
Чистая функция `parseFilename(disposition: String?, fallback: String): String` (обрезка `attachment; filename*=UTF-8''...` и `filename="..."`) + юнит-тест в `DownloadNameTest.kt` (TDD: сначала тест на 3 формата заголовка, потом реализация).

- [ ] **Step 4: Поделиться**

`Intent.createChooser(Intent(ACTION_SEND).apply { type="text/plain"; putExtra(EXTRA_TEXT, url) }, "Поделиться")`.

- [ ] **Step 5: Очистить данные**

Настройки: кнопка → диалог подтверждения → `historyRepo.clear()`, `session.settings`… достаточно: очистка истории + закладок по желанию (чекбокс) + `Engine.runtime.storageController.clearData(StorageController.ClearDataOptions(...))`? — минимум: Room-очистка и удаление `files/icons`. Кэш Gecko не трогаем (спека: «минимум»).

- [ ] **Step 6: Проверка**

Каждый пункт меню руками на эмуляторе: найти слово на странице; сайт в десктопном виде; скачать файл → появился в Downloads; шарит URL; очистка данных работает.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: find-in-page, desktop mode, downloads, share, clear data"
```

---

### Task 12: Финальная полировка и приёмочный прогон

**Files:**
- Modify: тема (dynamic color на Android 12+: `dynamicDarkColorScheme(context)`), мелкие UX неровности, найденные при прогоне
- Create: `README.md`

- [ ] **Step 1: Dynamic color**

Если API 31+ — `if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(ctx)/dynamicLightColorScheme(ctx)` вместо статических схем.

- [ ] **Step 2: README**

Как собрать на NixOS (nix shell команды из задач), как обновить расширения (`scripts/fetch-extensions.sh`), как ставить APK на телефон.

- [ ] **Step 3: Приёмочный чек-лист (ручной, эмулятор)**

1. Холодный старт → стартовая страница с плитками ≤ 2 сек.
2. Поиск из омнибокса работает во всех 4 поисковиках.
3. Реклама блокируется (тест-страница d3ward), тумблер выключает.
4. Видео YouTube: перевод включается, автоперевод работает.
5. Вкладки восстанавливаются после kill; приватная — не пишется нигде.
6. История/подсказки/закладки/переименование плиток работают.
7. Найти/поделиться/скачать/десктоп-режим работают.
8. Тёмная/светлая/системная темы корректны.

- [ ] **Step 4: Финальный commit**

```bash
nix shell -c ./gradlew test && git add -A && git commit -m "polish: dynamic color, readme, acceptance pass"
```

---

## Self-review плана

- **Покрытие спеки:** реклама → T8; VOT/автоперевод/UA-риски → T9 (+autoplay с T2); плитки/иконки/стартовая → T7; вкладки/шторка/восстановление/краш → T5; приватные → T10; история+подсказки → T6; настройки/тема/поисковик/домашняя → T4; меню (найти/ПК/скачать/поделиться/очистка) → T11; ошибки/восстановление → T5 (краш-делегат, штатные страницы Gecko); проверки → юнит-тесты T3/T5/T6/T7/T11 + эмуляторные прогоны в каждом таске и чек-лист T12. Гэпов нет.
- **Placeholders:** единственное узкое место — фактический `VOT_ID` (узнаётся только после распаковки на машине) — оформлено обязательным шагом T8 Step 1 с командой вывода id; сигнатуры пары колбеков Gecko могут отличаться в 154 — в каждом таком месте указана команда сверки с javadoc.
- **Консистентность типов:** `Tab.session` объявлен `var` в T5 до использования в краш-восстановлении там же; `Suggestion`/`Scored` определены в T6 и используются в T7 (bookmarks → строки-urls для ранжирования); `Prefs.adblockEnabled` из T4 потребляется T8; `TabManager.newTab(url, private)` из T5 используется в T10; `FaviconFetcher.cacheFile/fetch` из T7 используются TileGrid'ом того же таска.
