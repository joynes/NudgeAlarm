# Release Upload Instructions

## Java

Use the JDK bundled with Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## Run All Tests

```bash
./gradlew test
```

## Build Release APK

```bash
./gradlew assembleRelease
```

The signed release APK is generated at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Upload

```bash
./upload-apk.sh
```

The uploaded APK is always named:

```text
latest.apk
```
