# Tests and coverage

The GitHub workflow `.github/workflows/tests.yml` runs on every branch push,
pull request and manual dispatch. A local commit triggers GitHub only after it
is pushed. Test jobs do not need the private release keystore. Release signing
still uses the existing local signing configuration.

## Run locally

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_SERIAL=emulator-5556 # Select your own test emulator explicitly.
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew combinedDebugCoverage
python3 scripts/check_coverage.py app/build/reports/jacoco/combinedDebugCoverage/combinedDebugCoverage.xml --minimum 95
```

The report combines JVM `.exec` and emulator `.ec` execution data. Open
`app/build/reports/jacoco/combinedDebugCoverage/html/index.html` to inspect
uncovered source lines. CI uploads reports and raw test results as an artifact
even when a test or the coverage target fails.

The target is **95% line coverage across all handwritten application code**,
including UI, ViewModels, services and location handling. Generated `R`,
`BuildConfig` and Room `*_Impl` classes are excluded. No handwritten package is
excluded. Branch coverage is also reported but has no separate threshold.
The gate uses the unrounded percentage; 94.999% must fail.

## Emulator requirements

Use API 36 with Google Play services, Location enabled, and Google Location
Accuracy enabled. `GeofenceMonitorIntegrationTest` deliberately calls real
Google Play services. It fails when that provider is unavailable; this is not
silently skipped or counted as a passing test. See `upload.md` for setup.

Use a separate AVD when another project uses the default emulator. Do not reset
or uninstall another project's app to obtain disk space. Release and debug apps
have separate package IDs.

## Verified baseline

On 2026-11-04, version 1.173 passed all 291 JVM tests, all 246 emulator tests
and all 3 tests of the coverage gate, with no skipped tests. Combined whole-app
line coverage was **95.32% (8,694 / 9,121 executable lines)**. The signed release
APK and AAB built successfully; the release APK was also installed and cold
started on the separate emulator.

[GitHub run 41737761045](https://github.com/joynes/NudgeAlarm/actions/runs/41737761045)
independently passed the same tests and the 95% gate on Linux/API 36, with
**95.25% (8,688 / 9,121 executable lines)**. Both measurements include all
handwritten application code.

The gate tests check the real script: exactly 95% passes, 94.999% fails despite
rounding to 95.00% for display, and a report without executable lines fails.
Remaining uncovered lines stay in the denominator. The gate is applied afresh
on every run; this baseline does not exempt later changes.

See [GitHub Actions](https://github.com/joynes/NudgeAlarm/actions/workflows/tests.yml)
for the actual job result and downloadable HTML/XML coverage and test reports.
