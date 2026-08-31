# Screenshot regression references

This directory contains the committed Roborazzi golden images used by JVM screenshot tests.

- `design_foundation_light.png` covers the light theme with the normal system font scale.
- `design_foundation_dark_large_text.png` covers the dark theme at 200% font scale.

To intentionally update the references after reviewing a UI change:

```bash
./gradlew :app:recordRoborazziDebug
```

Normal CI must not regenerate references. It runs:

```bash
./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug
```

A screenshot mismatch is a regression until the UI change and the updated reference have both been reviewed.
