# Tests and coverage

The GitHub workflow `.github/workflows/tests.yml` runs on every branch push,
pull request and manual dispatch. A local commit triggers GitHub only after it
is pushed. Test jobs do not need the private release keystore. Release signing
still uses the existing local signing configuration.

## Run locally

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_SERIAL=emulator-5556 # Select your own test emulator explicitly.
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

## Current status

Coverage instrumentation and CI were added in November 2026. The existence of
the 95% gate does **not** mean the target has been achieved. The generated report
and the matching GitHub run are the evidence; a red gate is an unmet target.
