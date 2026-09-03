# Physical-device QA status

Последнее обновление: 2026-09-03.

## Последний полный physical-device проход

- Git SHA: `5629a3c09a897ba28a1414e52b0966a249a3e137` (ветка `master` на момент проверки)
- APK SHA256: `8b54a1c00f8781daaf9c6f47dd5bd0ec1e5cc3338429517f9983803432cc56bb`
- Install mode: `adb install -r`, пользовательские данные сохранены
- Device: OnePlus CPH2723 / OnePlus 13s
- Android: 16 / API 36
- ABI: `arm64-v8a`
- PAGE_SIZE: 4096

## Автоматические проверки physical-device SHA

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

## Интеграционный набор `ux-tab-polish`

Перед слиянием в `master` набор проверяется тремя независимыми GitHub Actions workflows:

- `Android`: unit/screenshot tests, lint, debug/release packaging, R8/resource shrink, baseline profiles, benchmark/profile APK, API 36 instrumentation, minSdk 26 startup smoke, 16 KB page-size lane и Android 17 compatibility lane;
- `Room Schema`: KSP повторно генерирует `app/schemas/com.artt.minibrowser.data.AppDb/3.json` и проверяет отсутствие schema drift;
- `Bundled Extensions`: повторно скачивает XPI из `extensions.lock`, проверяет SHA256/version/id и сравнивает распакованные assets с содержимым репозитория.

Android 17 lane использует явный target-package Intent для startup/recreation tests, чтобы AndroidX Test не пытался разрешать `MainActivity` внутри test APK package.

Отдельный physical-device проход именно для `ux-tab-polish` до слияния не выполнялся.

## Physical-device результаты последнего прохода

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
| Private mode | PASS | пользовательский viewport отображается нормально; screen capture защищён `FLAG_SECURE` |
| External intent | PASS (эмулятор) | `mailto:` → Gmail, `tel:` → Dialer, `topResumedActivity` меняется; на устройстве не перепроверялось |
| HTTP | NOT RUN | не выполнялось в этом physical-device проходе |
| Bookmarks full flow | NOT RUN | не выполнялось в этом проходе |
| Extensions full lifecycle | NOT RUN | не выполнялось в этом проходе |
| Dark mode physical pass | NOT RUN | не выполнялось в этом проходе |
| Exit animation acceptance | NOT RUN | не выполнялось в этом проходе |

## Network policy

MiniBrowser принимает `http://` и `https://` как браузерные схемы. Android manifest явно разрешает cleartext traffic, поэтому платформенный same-origin favicon fetcher и Gecko navigation не расходятся по базовой политике HTTP.

## Performance limitation

На production user-build Android benchmark instrumentation может не иметь `android.permission.CLEAR_APP_USER_DATA`. В таком окружении сценарии, которым нужен `pm clear`, нельзя считать полноценным baseline/profile performance comparison; это ограничение test environment, а не доказанный production crash.

## Оставшийся manual/device QA

После интеграции остаётся вручную проверить на физическом устройстве HTTP, bookmarks, extensions lifecycle, dark mode, exit animations, TalkBack spoken navigation и predictive back. External VIEW intent проверен на эмуляторе; physical-device перепроверка остаётся желательной.
