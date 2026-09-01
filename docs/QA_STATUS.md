# Physical-device QA status

Последнее обновление: 2026-09-02.

## Проверенная сборка

- Git SHA: `5629a3c09a897ba28a1414e52b0966a249a3e137`
- APK SHA256: `8b54a1c00f8781daaf9c6f47dd5bd0ec1e5cc3338429517f9983803432cc56bb`
- Install mode: `adb install -r`, пользовательские данные сохранены
- Device: OnePlus CPH2723 / OnePlus 13s
- Android: 16 / API 36
- ABI: `arm64-v8a`
- PAGE_SIZE: 4096

## Автоматические проверки

| Проверка | Результат |
| --- | --- |
| Unit tests | PASS |
| Lint | PASS |
| Debug build | PASS |
| AndroidTest build | PASS |
| Full instrumentation | PASS — 66 tests |
| Omnibox instrumentation | PASS — 6 tests |
| Startup/recreation instrumentation | PASS — 2 tests |
| GitHub Actions | PASS — run `33563450818` |
| Production crash/ANR scan | PASS |

## Physical-device результаты

| Сценарий | Результат | Примечание |
| --- | --- | --- |
| Omnibox normal height | PASS | compact chrome |
| Omnibox focused full width | PASS | соседние действия скрываются |
| Omnibox focused fixed height | PASS | вертикального stretch нет |
| Continuous typing | PASS | focus и IME сохраняются |
| Suggestions | PASS | bounded popup, focus сохраняется |
| IME Search | PASS | navigation выполнена |
| Back from omnibox | PASS | editing закрывается до browser back |
| Menu quick actions | PASS | Private / History / Bookmarks, одна строка |
| Settings normal scroll | PASS | fake scroll отсутствует |
| Settings large-font scroll | PASS | scroll работает при overflow |
| HTTPS | PASS | реальная navigation |
| Tabs | PARTIAL | основные операции проверены |
| 10 tabs | PASS | UI count 10, memory captured |
| History | PASS | записи отображаются |
| Rotation | PASS | Activity остаётся resumed |
| Large font | PASS | layout/scroll проверены |
| Accessibility automated checks | PASS | instrumentation suite |
| TalkBack spoken navigation | MANUAL CHECK REQUIRED | требует ручной оценки |
| Private mode | FAIL | persistent black screen |
| External intent | NOT VALIDATED | тестовый VIEW intent перехватил другой установленный browser |
| HTTP | NOT RUN | QA остановлен после blocker |
| Bookmarks full flow | NOT RUN | QA остановлен после blocker |
| Extensions full lifecycle | NOT RUN | QA остановлен после blocker |
| Dark mode physical pass | NOT RUN | QA остановлен после blocker |
| Exit animation acceptance | NOT RUN | QA остановлен после blocker |

## Blocking known issue

### Private tab -> persistent black screen

Issue: https://github.com/arttvad9r/minibrowser/issues/2

Reproduction:

1. Открыть главное меню.
2. Нажать «Приватная вкладка».
3. Подождать 15+ секунд.

Expected: отображается usable private tab/start page.

Actual: весь viewport приложения становится чёрным и остаётся чёрным. Activity остаётся `RESUMED`; production crash, ANR и `FATAL EXCEPTION` не обнаружены.

Severity: **High**.

Этот дефект должен оставаться явно отслеживаемым после интеграции в `master` и не должен быть скрыт зелёным CI: автоматические тесты не воспроизводят device-only failure.

## Performance limitation

На production user-build Android benchmark instrumentation может не иметь `android.permission.CLEAR_APP_USER_DATA`. В таком окружении сценарии, которым нужен `pm clear`, нельзя считать полноценным baseline/profile performance comparison; это ограничение test environment, а не доказанный production crash.

## Acceptance gates после merge

Перед объявлением private mode production-ready требуется:

1. воспроизвести issue #2 с диагностикой состояния active tab / GeckoSession / Compose route;
2. исправить root cause;
3. добавить regression test;
4. повторить private browsing flow на физическом устройстве;
5. завершить оставшиеся manual/device scenarios: HTTP, external VIEW intent с явным package/component при необходимости, bookmarks, extensions, dark mode, exit animations, TalkBack и predictive back.
