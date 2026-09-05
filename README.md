# Minibrowser

Личный минималистичный Android-браузер на GeckoView с Jetpack Compose UI, встроенными WebExtensions и локальным хранением пользовательских данных. Проект не предназначен для публикации как готовый массовый продукт.

## Возможности

- GeckoView как браузерный движок;
- обычные и приватные вкладки, восстановление обычных вкладок и Gecko session state;
- омнибокс с историей и подсказками, Back / Forward / Reload;
- история и визуальные закладки;
- uBlock Origin и VOT как built-in WebExtensions;
- поиск на странице, desktop mode, share, permissions и Gecko prompts;
- загрузки через MediaStore на Android 10+ и public Downloads на Android 8–9;
- явная поддержка `http://` и `https://` навигации;
- локальный favicon-кэш без стороннего favicon proxy;
- светлая/тёмная тема, крупный шрифт, accessibility semantics и edge-to-edge UI;
- Baseline/Startup Profiles и Macrobenchmark-сценарии.

## Android / build configuration

- `applicationId`: `com.artt.minibrowser`
- `minSdk`: 26
- `targetSdk`: 36
- `compileSdk`: 37.1
- Java/Kotlin target: 17
- GeckoView: `154.0.20260814215756`
- локальные APK по умолчанию собираются только для `arm64-v8a`;
- CI переопределяет ABI на `x86_64` для emulator lanes;
- release включают R8/minify и resource shrinking.

## Сборка

Нужны JDK 17 и Android SDK. Локальный путь SDK хранится в `local.properties` и не коммитится.

На NixOS:

```bash
nix shell nixpkgs#temurin-bin-17 -c ./gradlew assembleDebug
```

Обычный Gradle запуск:

```bash
./gradlew assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Установка/обновление на подключённое устройство без очистки данных:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Проверки

Базовый локальный набор:

```bash
./gradlew \
  testDebugUnitTest \
  lintDebug \
  assembleDebug \
  assembleDebugAndroidTest \
  assembleRelease \
  bundleRelease
```

Screenshot tests используют Roborazzi; эталонные изображения находятся в `app/src/test/snapshots/`.

GitHub Actions дополнительно проверяет:

- unit/screenshot tests и lint;
- debug/release APK и AAB;
- instrumentation suite;
- startup/recreate smoke на `minSdk 26`;
- Android 36 / API 36 instrumentation;
- 16 KB page-size emulator lane с фактической проверкой `PAGE_SIZE=16384`;
- native/ZIP alignment;
- Baseline/Startup Profile packaging;
- benchmark/profile APK packaging;
- Android 17 preview как неблокирующий compatibility diagnostic;
- соответствие сгенерированной Room schema её committed JSON-версии;
- соответствие bundled uBlock/VOT содержимому и SHA256 из `extensions.lock`.

Android 17 preview lane намеренно не является release gate: текущий API 37 preview system image `CE2A.260420.019` аварийно завершает системный `surfaceflinger` в `RegionSamplingThread` во время instrumentation. Ошибка воспроизводится и на обычном 4 KB image, и на 16 KB image, а также с разными software graphics backends; в crash stack нет app-level `FATAL EXCEPTION` MiniBrowser. До обновления preview image/emulator этот lane считается инфраструктурной диагностикой, а не подтверждением или опровержением совместимости приложения.

Для GeckoView, IME, permissions, fullscreen, downloads, WebExtensions, predictive back и device-specific UI обязательна дополнительная проверка на физическом Android-устройстве.

## Архитектура

Основной поток состояния:

```text
Compose UI -> ViewModel/state holder -> repository/data layer
```

- `BrowserApp` владеет process-level GeckoRuntime и process-scoped browser services;
- `TabManager` управляет GeckoSession, жизненным циклом вкладок и persistence;
- Room используется для структурированных данных, DataStore — для настроек;
- GeckoView присоединяется к session только на время отображения, а закрытие session остаётся ответственностью `TabManager`;
- private tabs не должны попадать в persisted tab state, history или preview cache.

## Расширения

Built-in extensions находятся в:

```text
app/src/main/assets/extensions/
```

uBlock Origin и VOT устанавливаются через GeckoView WebExtension API. Состояние enable/disable хранится приложением и применяется через `ExtensionLoader`.

Исходные XPI зафиксированы в `extensions.lock` по версии, extension id, URL и SHA256. CI повторно скачивает их, проверяет checksum и сравнивает распакованное содержимое с bundled assets.

## Data schema

Room schema экспортируется в `app/schemas/` и хранится в git. CI повторно запускает KSP и отклоняет изменение схемы, если соответствующий JSON не обновлён вместе с кодом/миграцией.

## Performance

Модуль `benchmark/` содержит Macrobenchmark/Baseline Profile сценарии. Release-like benchmark variant использует оптимизированную конфигурацию, а profile variant остаётся отдельным для получения стабильных unobfuscated profile rules.

Сгенерированные правила входят в приложение:

```text
app/src/main/baselineProfiles/baseline-prof.txt
app/src/main/baselineProfiles/startup-prof.txt
```

## Текущий QA status

`ui-polish-audit` / PR #5 прошёл полный automated + physical-device acceptance на tested code SHA `8dca897d610dfd0d4e53df4a7c3cddc326f84c32`.

Physical device: OnePlus 13s (CPH2723), Android 16 / API 36 / arm64-v8a. Host tests, Roborazzi, lint и APK builds прошли; startup/recreation — 2/2 PASS; physical connected instrumentation — 69/69 PASS, 0 skipped, 0 failed; app-level crash/ANR не обнаружены.

Ручной physical pass завершён для normal/private tabs, 10+ tabs и zero-tabs + Undo, rapid multi-close, HTTP/HTTPS, omnibox/IME, downloads, permissions, bundled extensions, bookmarks/history, find, desktop mode, share, external intents, light/dark theme, font scale 2x, TalkBack semantics, predictive back, rotation/background-foreground и internal empty/error states.

Итог для tested code SHA: **READY FOR MERGE**.

Android 17/API 37 остаётся отдельным неблокирующим preview diagnostic: системный `surfaceflinger` / `RegionSamplingThread` падает до завершения instrumentation, без зафиксированного app-level MiniBrowser `FATAL EXCEPTION`. 16 KB compatibility независимо подтверждена стабильной API 36 PS16K lane.

Подробный статус и ограничения: [`docs/QA_STATUS.md`](docs/QA_STATUS.md).

## Known issues

- Android 17/API 37 preview emulator `CE2A.260420.019` падает в системном `surfaceflinger`/`RegionSamplingThread` до завершения compatibility instrumentation; lane остаётся неблокирующим до исправления preview infrastructure.
- Полный performance/baseline comparison на обычном production user build может быть ограничен отсутствием `android.permission.CLEAR_APP_USER_DATA` у benchmark instrumentation.

## Ветки

Главная ветка репозитория — `master`. Feature-ветки интегрируются только после автоматических проверок; документация и CI в `master` должны описывать фактически слитое состояние.
