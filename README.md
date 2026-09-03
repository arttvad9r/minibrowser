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
- Android 17 preview как неблокирующий compatibility diagnostic.

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

## Performance

Модуль `benchmark/` содержит Macrobenchmark/Baseline Profile сценарии. Release-like benchmark variant использует оптимизированную конфигурацию, а profile variant остаётся отдельным для получения стабильных unobfuscated profile rules.

Сгенерированные правила входят в приложение:

```text
app/src/main/baselineProfiles/baseline-prof.txt
app/src/main/baselineProfiles/startup-prof.txt
```

## Текущий QA status

Последний полный physical-device проход выполнялся на OnePlus 13s / Android 16 / API 36 / arm64-v8a, на SHA `5629a3c` в `master`. На проверенном SHA прошли host build checks, полный instrumentation suite (`66` tests), startup/recreation, омнибокс, 10 вкладок, rotation, history, large-font и основные accessibility checks.

Ветка `ux-tab-polish` проверена только на эмуляторе Android 17 / API 37: unit (`298`), lint, debug/release build и полный instrumentation suite (`67` tests). Physical-device проход для неё не выполнялся.

Подробный статус и ограничения: [`docs/QA_STATUS.md`](docs/QA_STATUS.md).

## Known issues

- Полный performance/baseline comparison на обычном production user build может быть ограничен отсутствием `android.permission.CLEAR_APP_USER_DATA` у benchmark instrumentation.
- TalkBack spoken navigation и субъективное качество predictive-back требуют ручной acceptance-проверки на реальном устройстве.

## Ветки

Главная ветка репозитория — `master`. После интеграции feature-веток документация и CI должны отражать состояние `master`; долгоживущие ветки не должны содержать отдельные незамерженные исправления без явной причины.

Актуальное отклонение: ветка `ux-tab-polish` содержит 15 незамерженных коммитов сверх `master` (undo закрытия вкладки, поиск по вкладкам, morph-переход в переключатель, компактное overflow-меню, prewarm превью). До мержа `master` не отражает текущее состояние UI.
