# Physical-device QA status

Последнее обновление: 2026-09-03.

## Проверенная сборка

- Git SHA: `5629a3c09a897ba28a1414e52b0966a249a3e137` (ветка `master`)
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

## Ветка `ux-tab-polish` (эмулятор, 2026-09-03)

Проверено на `af5513f` + правки этого прохода. Эмулятор `opencode-main-phone`, Android 17 / API 37 / x86_64.

| Проверка | Результат |
| --- | --- |
| Unit tests | PASS — 298 tests |
| Lint | PASS — 23 warning, 0 error |
| Debug build (arm64 + x86_64) | PASS |
| Release build (R8 + shrink) | PASS |
| Full instrumentation | PASS — 67 tests |

До правок этого прохода instrumentation на API 37 давал 13 PASS / 54 FAIL: `ui-test-junit4` тянул
`espresso-core:3.5.0`, чей `InputManagerEventInjectionStrategy` вызывает удалённый в Android 17
приватный `InputManager.getInstance()`. Lane CI с API 36 этот класс отказов не воспроизводит,
поэтому полный набор теперь гоняется и на API 37.

Physical-device проход для этой ветки не выполнялся.

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
| Private mode | PASS | пользовательский viewport отображается нормально; screen capture защищён `FLAG_SECURE` |
| External intent | PASS (эмулятор) | `mailto:` → Gmail, `tel:` → Dialer, `topResumedActivity` меняется; на устройстве не перепроверялось |
| HTTP | NOT RUN | не выполнялось в этом проходе |
| Bookmarks full flow | NOT RUN | не выполнялось в этом проходе |
| Extensions full lifecycle | NOT RUN | не выполнялось в этом проходе |
| Dark mode physical pass | NOT RUN | не выполнялось в этом проходе |
| Exit animation acceptance | NOT RUN | не выполнялось в этом проходе |

## Performance limitation

На production user-build Android benchmark instrumentation может не иметь `android.permission.CLEAR_APP_USER_DATA`. В таком окружении сценарии, которым нужен `pm clear`, нельзя считать полноценным baseline/profile performance comparison; это ограничение test environment, а не доказанный production crash.

## Acceptance gates после merge

Для завершения manual/device QA остаётся проверить HTTP, bookmarks, extensions, dark mode, exit animations, TalkBack и predictive back (external VIEW intent проверен на эмуляторе, на устройстве — нет).
