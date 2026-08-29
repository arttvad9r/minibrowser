# Minibrowser

Личный минималистичный Android-браузер на GeckoView: встроенный uBlock Origin,
закадровый перевод видео (VOT), визуальные закладки-плитки. Не для публикации.

## Сборка (NixOS)

Нужны JDK 17 и Android SDK (`~/Android/Sdk`, путь прописывается в `local.properties`,
файл не коммитится):

```bash
nix shell nixpkgs#temurin-bin-17 -c ./gradlew assembleDebug
# артефакт: app/build/outputs/apk/debug/app-debug.apk
```

Тесты: `nix shell nixpkgs#temurin-bin-17 -c ./gradlew test`

## Установка

```bash
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Обновление расширений (uBO / VOT)

Расширения лежат распакованными в `app/src/main/assets/extensions/{ublock,vot}`
и ставятся как built-in WebExtensions (`ensureBuiltIn`). Обновить:

```bash
nix shell nixpkgs#curl nixpkgs#unzip -c ./scripts/fetch-extensions.sh
```

Скрипт печатает фактические id расширений — они уже вписаны в
`engine/ExtensionLoader.kt`; при смене обновить константы.

## Проверки

GitHub Actions выполняет unit tests, lint, debug/release-сборки и instrumentation-тесты
на Android emulator. Для изменений GeckoView дополнительно нужен smoke-test на реальном
устройстве, поскольку ввод, permissions, fullscreen, загрузки и WebExtensions зависят от
системного UI и конкретного Android окружения.

Основные сценарии приложения:

- uBO: блокировка сети и косметическая фильтрация; тумблер включает/выключает расширение на лету;
- VOT: перевод поддерживаемых видеостраниц;
- вкладки и восстановление после kill, приватные вкладки без истории/персистентности;
- история, подсказки омнибокса и визуальные закладки;
- favicon-кэш получает только same-origin `/favicon.ico` и не использует сторонние favicon proxy;
- найти на странице, версия для ПК, скачивания, поделиться, очистка данных;
- VIEW-интенты и безопасная обработка внешней навигации.
