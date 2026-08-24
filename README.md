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

## Проверено

- uBO: блокировка сети (doubleclick) и косметическая фильтрация (`#adsbox`),
  тумблер «Блокировка рекламы» включает/выключает расширение на лету.
- VOT: кнопка «Translate video» инжектится в плеер VK Video; YouTube из РФ-сети
  недоступен — полный цикл перевода проверять на устройстве.
- Вкладки/восстановление после kill, приватные вкладки (без истории/персистентности),
  история + подсказки омнибокса, закладки-плитки с favicon-кэшем (DDG),
  найти на странице, версия для ПК, скачивания, поделиться, очистка данных,
  VIEW-интенты (открытие ссылок из других приложений).
