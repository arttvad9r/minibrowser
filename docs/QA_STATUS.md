# Physical-device QA status

Последнее обновление: 2026-09-05.

## Текущий acceptance: `ui-polish-audit` / PR #5

Полный UI/QA-набор `ui-polish-audit` принят на физическом устройстве.

- Tested code SHA: `8dca897d610dfd0d4e53df4a7c3cddc326f84c32`
- Device: OnePlus CPH2723 / OnePlus 13s
- Android: 16 / API 36
- ABI: `arm64-v8a`
- PAGE_SIZE: 4096
- Install mode: debug APK + AndroidTest APK через `adb install -r`; данные основного приложения не очищались
- Final verdict: **READY FOR MERGE**

После physical acceptance в ветку могут добавляться только QA/documentation-only коммиты. Они не меняют проверенный app/build/test code SHA выше и не требуют повторного physical acceptance, если diff действительно ограничен документацией.

## Автоматические проверки

GitHub Actions для tested code SHA завершены:

| Проверка | Результат |
| --- | --- |
| Android workflow #1167 | PASS — release gates successful; Android 17 diagnostic nonblocking |
| Unit + Roborazzi | PASS |
| Lint | PASS |
| Debug APK | PASS |
| Release APK + AAB | PASS |
| R8/resource shrinking | PASS |
| 16 KB native/ZIP alignment | PASS |
| Baseline/Startup Profile packaging | PASS |
| Benchmark/profile APK packaging | PASS |
| API 36 emulator instrumentation | PASS — 69 tests |
| minSdk 26 startup smoke | PASS |
| API 36 16 KB page-size lane | PASS — `PAGE_SIZE=16384` |
| Room Schema workflow #31 | PASS |
| Bundled Extensions workflow #29 | PASS |

## Physical-device automation

На OnePlus CPH2723 / API 36:

| Проверка | Результат |
| --- | --- |
| Host unit tests | PASS |
| Roborazzi verification | PASS |
| Lint | PASS |
| Debug APK build | PASS |
| AndroidTest APK build | PASS |
| APK metadata | PASS — package `com.artt.minibrowser`, minSdk 26, targetSdk 36, arm64-v8a |
| Startup/recreation | PASS — 2/2 |
| Full connected instrumentation | PASS — 69/69, 0 skipped, 0 failed |
| App-level crash scan | PASS — 0 |
| App-level ANR scan | PASS — 0 |

Первый запуск instrumentation был заблокирован старым `com.artt.minibrowser.test` с другой подписью. После явного удаления только test package exact-SHA AndroidTest APK установился корректно; startup/recreation и полный suite прошли. Основной APK и test APK после тестов были восстановлены, `MainActivity` финально запущена и находилась в focus.

## Physical-device manual acceptance

Ручной проход на том же tested code SHA завершён без найденных блокирующих дефектов.

| Сценарий | Результат |
| --- | --- |
| Main launch / background-foreground / rotation | PASS |
| HTTP и HTTPS navigation | PASS |
| Omnibox focus, continuous input, suggestions, IME Search, Back | PASS |
| Normal/private tabs | PASS |
| 10+ tabs, rapid multi-close, zero tabs + Undo | PASS |
| Tab overview, search и empty/no-results states | PASS |
| Overflow menu и touch interaction | PASS |
| Settings normal scroll | PASS |
| Settings при font scale 2.0 | PASS |
| Light / dark / system theme | PASS |
| Bottom sheets: action, Cancel, Back, gesture dismiss, повторное открытие | PASS |
| Bookmarks add/open/rename/delete | PASS |
| History lifecycle | PASS |
| Downloads и открытие файла | PASS |
| Camera / microphone / location Allow/Deny flows | PASS |
| uBlock Origin / VOT enable-disable lifecycle | PASS |
| Find in page | PASS |
| Desktop mode | PASS |
| Share chooser | PASS |
| `mailto:` / `tel:` external intents | PASS |
| Back route matrix | PASS |
| Predictive Back / route animation acceptance | PASS |
| TalkBack focus order / labels / switch-state announcement | PASS |
| Empty/error internal-screen states | PASS |

Private mode проверялся по реальному viewport устройства. Защита screen capture через `FLAG_SECURE` является ожидаемым поведением и не считается UI-дефектом.

## Android 17 / API 37 preview diagnostic

Android 17 compatibility остаётся отдельным неблокирующим diagnostic lane. В Android workflow #1167 стабильные release gates прошли, а job `android-17-compat` снова завершился на preview emulator/system infrastructure во время startup/recreation/instrumentation.

Ранее этот сбой был локализован в системном `surfaceflinger` / `RegionSamplingThread` с assertion через GoldfishMapper/GraphicBufferMapper. Он воспроизводился на 4 KB и 16 KB preview images и с разными software graphics backends; app-level `FATAL EXCEPTION` MiniBrowser до системного crash не фиксировался.

16 KB compatibility независимо подтверждена стабильной API 36 PS16K lane. API 37 automation следует повторить после обновления preview system image/emulator; текущий diagnostic не блокирует merge принятого API 36 physical build.

## Network policy

MiniBrowser принимает `http://` и `https://` как браузерные схемы. Android manifest явно разрешает cleartext traffic. Реальная HTTP-навигация подтверждена physical-device acceptance.

## Performance limitation

На production user-build Android benchmark instrumentation может не иметь `android.permission.CLEAR_APP_USER_DATA`. Это ограничение benchmark environment и не является доказанным production defect.

## Итог

Для tested code SHA `8dca897d610dfd0d4e53df4a7c3cddc326f84c32` обязательные автоматические и physical-device проверки завершены.

**READY FOR MERGE.**
