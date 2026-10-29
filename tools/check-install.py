#!/usr/bin/env python3
"""Shared Android installation evidence for manual, AI and shell installation.

inspect is read-only. verify opens Settings and sends HOME (never sleep/reboot).
Exit 0: requested check passed; 1: failed; 2: unverified/error.
An ADB HOME check never certifies the physical remote, wake or boot.
"""
import argparse
import json
import re
import subprocess
import time
from pathlib import Path

PACKAGE = "com.leslie.tvhome"
HOME = PACKAGE + "/.HomeActivity"
HOME_ARGS = ["-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"]


def component(value):
    if not re.fullmatch(r"[\w.]+/[\w.$]+", value):
        return None
    package, activity = value.split("/", 1)
    return package + "/" + (package + activity if activity.startswith(".") else activity)


def focus(text):
    # mFocusedApp in a window dump can be stale behind a dialog. Only mCurrentFocus counts here.
    matches = re.findall(r"mCurrentFocus[^\n]*?\b([\w.]+/[\w.$]+)", text or "")
    values = {component(value) for value in matches}
    values.discard(None)
    return values.pop() if len(values) == 1 else None


def focused_app(text):
    """The resumed activity from `dumpsys activity activities` (mFocusedApp)."""
    matches = re.findall(r"mFocusedApp[^\n]*?\b([\w.]+/[\w.$]+)", text or "")
    values = {component(value) for value in matches}
    values.discard(None)
    return values.pop() if len(values) == 1 else None


class Check:
    def __init__(self, serial, run=subprocess.run, pause=time.sleep):
        self.serial, self.run, self.pause = serial, run, pause
        self.evidence = []

    def shell(self, *args):
        try:
            result = self.run(["adb", "-s", self.serial, "shell", *args],
                              capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20)
            output = result.stdout.strip()
            self.evidence.append({"command": list(args), "exit": result.returncode,
                                  "stdout": output, "stderr": result.stderr.strip()})
            return output if result.returncode == 0 else None
        except (OSError, subprocess.TimeoutExpired) as error:
            self.evidence.append({"command": list(args), "error": str(error)})
            return None

    def inspect(self):
        device = {name: self.shell("getprop", prop) for name, prop in {
            "manufacturer": "ro.product.manufacturer", "brand": "ro.product.brand",
            "model": "ro.product.model", "firmware": "ro.build.version.incremental",
            "fingerprint": "ro.build.fingerprint", "sdk": "ro.build.version.sdk"}.items()}
        self.shell("cmd", "package", "query-activities", "--brief", *HOME_ARGS)
        self.shell("cmd", "package", "resolve-activity", "--brief", *HOME_ARGS)
        self.shell("settings", "get", "secure", "enabled_accessibility_services")
        self.shell("dumpsys", "accessibility")
        path = self.shell("pm", "path", PACKAGE)
        installed = "passed" if path and re.search(r"^package:/", path, re.M) else "unverified"
        return {"platform": "android", "device": device, "installation": installed,
                "adb_home": "unverified", "physical_home": "unverified",
                "standby_wake": "unverified", "reboot": "unverified"}

    def verify(self, expected):
        expected = component(expected)
        if not expected:
            raise ValueError("Invalid expected HOME component")
        # Starting the desktop before pressing HOME would allow a false positive.
        started = self.shell("am", "start", "-W", "-a", "android.settings.SETTINGS")
        if started is None or re.search(r"Error|Exception", started, re.I):
            return "unverified"
        self.pause(1)
        baseline = self.read_focus()
        if not baseline or baseline == expected:
            return "unverified"
        if self.shell("input", "keyevent", "KEYCODE_HOME") is None:
            return "unverified"
        observations = []
        # Observe the whole interval, not just a transient flash of the desktop.
        for _ in range(8):
            self.pause(1)
            observations.append(self.read_focus())
        if observations[-3:] == [expected] * 3:
            return "passed"
        return "failed" if all(observations[-3:]) else "unverified"

    def read_focus(self):
        # Prefer the activity dump. Fall back to the window dump when that command is absent.
        activity = self.shell("dumpsys", "activity", "activities") or ""
        found = focused_app(activity)
        if found:
            return found
        return focus(self.shell("dumpsys", "window") or "")

    def diagnose(self, result):
        brand = " ".join(result["device"].get(key) or "" for key in ("brand", "manufacturer")).lower()
        if result["adb_home"] == "passed":
            return "Test the physical remote, standby/wake and reboot separately."
        if any(name in brand for name in ("xiaomi", "redmi")):
            self.shell("settings", "get", "global", "disable_back_home_whitelist")
            return ("Check this firmware's HOME selection and guard binding. Package allowlist: "
                    "UNCONFIRMED. disable_back_home_whitelist concerns standby, not proof of a HOME allowlist. "
                    "Keep PatchWall enabled; do not rename the APK.")
        if any(name in brand for name in ("tcl", "ffalcon", "iffalcon")):
            return "Distinguish APK verification/HOME declaration from guard binding and autostart restrictions."
        return "Check actual HOME behavior, guard binding and firmware background restrictions; keep stock Home enabled."


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("inspect", "verify"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--expected", default=HOME)
    parser.add_argument("--report", type=Path)
    options = parser.parse_args()
    check = Check(options.serial)
    result = check.inspect()
    sdk = result["device"]["sdk"] or ""
    if not sdk.isdigit() or int(sdk) < 28:
        result["error"] = "Android 9+ required; non-Android TVs need their platform installer."
        code = 2
    else:
        if options.mode == "verify":
            result["adb_home"] = check.verify(options.expected)
            if component(options.expected) == component(HOME) and result["installation"] != "passed":
                result["adb_home"] = "unverified"
        result["next_step"] = check.diagnose(result) if options.mode == "verify" else "Read-only inventory; no HOME test performed."
        code = {"passed": 0, "failed": 1, "unverified": 2}[result["adb_home"]] if options.mode == "verify" else 0
    result["evidence"] = check.evidence
    text = json.dumps(result, ensure_ascii=True, indent=2)
    if options.report:
        options.report.write_text(text + "\n", encoding="utf-8")
    print(text)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
