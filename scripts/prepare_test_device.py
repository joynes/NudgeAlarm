"""Enable Google's network location provider on a disposable Android emulator."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--serial", default="emulator-5554")
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    raise SystemExit("This setup script is restricted to disposable emulators")

def adb(*command):
    return subprocess.check_output(["adb", "-s", args.serial, *command], text=True, timeout=30)

adb("shell", "cmd", "location", "set-location-enabled", "true")
adb("emu", "geo", "fix", "18.0", "59.3")
adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
adb("shell", "wm", "dismiss-keyguard")
for attempt in range(12):
    adb("shell", "am", "start", "-a", "com.google.android.gms.location.settings.LOCATION_ACCURACY")
    try:
        adb("shell", "uiautomator", "dump", "/sdcard/coverage-location.xml")
        root = ET.fromstring(adb("shell", "cat", "/sdcard/coverage-location.xml"))
    except (subprocess.CalledProcessError, ET.ParseError):
        # The system may finish booting before the first accessible window exists.
        time.sleep(2)
        continue
    switch = next((n for n in root.iter("node") if n.get("resource-id") == "android:id/switch_widget"), None)
    if switch is not None:
        if switch.get("checked") == "true":
            print("Google Location Accuracy is enabled")
            adb("shell", "input", "keyevent", "KEYCODE_HOME")
            break
        left, top, right, bottom = map(int, re.findall(r"\d+", switch.get("bounds")))
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(1)
else:
    raise SystemExit("Could not enable Google Location Accuracy; geofence tests cannot run reliably")
