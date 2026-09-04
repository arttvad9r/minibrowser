# Physical-device QA status

Последнее обновление: 2026-09-04.

## Последний полный physical-device проход

Это исторический acceptance-проход `master`, а не текущей audit-ветки.

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

## Текущий draft integration: `ui-polish-audit` / PR #5

`ui-polish-audit` — текущий draft-набор полного UI/QA-аудита. Он не считается physical-device accepted и не должен сливаться только на основании host/emulator CI.

Перед слиянием набор проверяется независимыми GitHub Actions workflows:

- `Android`: unit/screenshot tests, lint, debug/release packaging, R8/resource shrink, baseline profiles, benchmark/profile APK, API 36 instrumentation, minSdk 26 startup smoke, отдельная 16 KB page-size lane и неблокирующий Android 17 preview compatibility diagnostic;
- `Room Schema`: KSP повторно генерирует `app/schemas/com.artt.minibrowser.data.AppDb/3.json` и проверяет отсутствие schema drift;
- `Bundled Extensions`: повторно скачивает XPI из `extensions.lock`, проверяет SHA256/version/id и сравнивает распакованные assets с содержимым репозитория.

В audit-ветке дополнительно есть instrumentation-проверки minimum touch target и switch semantics главного меню, large-font reachability и startup/recreation target-package launch.

Отдельный physical-device проход именно для `ui-polish-audit` до слияния не выполнялся.

## Android 17 / API 37 preview blocker

Android 17 compatibility lane остаётся неблокирующим, потому что текущая preview-инфраструктура падает на уровне Android system до завершения тестов.

Последний зафиксированный diagnostic run для code-bearing SHA `18d7600a6d3a4cd5feb2041913389b962e0f2508` — Android workflow run `33856700101` (#1157). APK и AndroidTest APK успешно собираются, x86_64 native/ZIP alignment проходит, emulator загружается, приложение устанавливается после platform-level install retries и instrumentation стартует. Затем падает системный `surfaceflinger`:

```text
Fatal signal 6 (SIGABRT) ... tid RegionSampling ... surfaceflinger
Abort message: 'Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma'
```

Backtrace проходит через `GoldfishMapper::readFromHost`, `GraphicBufferMapper` и `RegionSamplingThread::threadMain`. После смерти SurfaceFlinger системные Google/Launcher процессы получают display/`DEAD_OBJECT` ошибки, а instrumentation завершается сообщением `INSTRUMENTATION_ABORTED: System has crashed.` App-level `FATAL EXCEPTION` MiniBrowser перед системным crash не зафиксирован.

Проблема воспроизводилась в нескольких конфигурациях preview emulator:

| API 37 конфигурация | PAGE_SIZE | Graphics | Результат |
| --- | ---: | --- | --- |
| `google_apis_ps16k` | 16384 | SwiftShader indirect | SYSTEM CRASH — SurfaceFlinger RegionSampling |
| `google_apis` | 4096 | SwiftShader indirect | SYSTEM CRASH — SurfaceFlinger RegionSampling |
| `google_apis` | 4096 | software / Lavapipe + SwANGLE | SYSTEM CRASH — SurfaceFlinger RegionSampling |

Следовательно, это не специфическая 16 KB ошибка MiniBrowser и не устраняется переходом на поддерживаемый software renderer. До появления стабильного API 37 preview image/emulator lane используется как diagnostic signal и не считается ни PASS, ни доказанным app regression.

16 KB compatibility проверяется независимо на стабильной API 36 PS16K lane; в run #1157 эта lane прошла с `PAGE_SIZE=16384`.

## Physical-device результаты последнего прохода `master`

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
| External intent | PASS (эмулятор) | `mailto:` → Gmail, `tel:` → Dialer; на устройстве не перепроверялось |
| HTTP | NOT RUN | не выполнялось в этом physical-device проходе |
| Bookmarks full flow | NOT RUN | не выполнялось в этом проходе |
| Extensions full lifecycle | NOT RUN | не выполнялось в этом проходе |
| Dark mode physical pass | NOT RUN | не выполнялось в этом проходе |
| Exit animation acceptance | NOT RUN | не выполнялось в этом проходе |

## Network policy

MiniBrowser принимает `http://` и `https://` как браузерные схемы. Android manifest явно разрешает cleartext traffic, поэтому платформенный same-origin favicon fetcher и Gecko navigation не расходятся по базовой политике HTTP. Реальный HTTP flow всё равно входит в оставшийся physical-device acceptance.

## Performance limitation

На production user-build Android benchmark instrumentation может не иметь `android.permission.CLEAR_APP_USER_DATA`. В таком окружении сценарии, которым нужен `pm clear`, нельзя считать полноценным baseline/profile performance comparison; это ограничение test environment, а не доказанный production crash.

## Оставшийся acceptance для `ui-polish-audit`

Перед переводом PR #5 из draft необходимо выполнить physical-device pass текущей ветки минимум по следующим сценариям:

- normal/private tabs, zero tabs + Undo, rapid multi-close и 10+ вкладок;
- HTTP и HTTPS navigation;
- downloads и открытие загруженного файла;
- camera/microphone/location permission allow/deny;
- bundled extensions lifecycle;
- rotation, background/foreground и process/activity recreation по возможности;
- dark mode и font scale 2x;
- TalkBack spoken navigation и switch-state announcement;
- predictive back и exit/navigation animation acceptance;
- external VIEW intents на реальном устройстве.

API 37 automation нужно повторить после обновления Android 17 preview system image/emulator. До этого текущий SurfaceFlinger crash должен оставаться явно помеченным как infrastructure blocker, а не скрываться общим зелёным workflow status.
