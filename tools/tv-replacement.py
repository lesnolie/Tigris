#!/usr/bin/env python3
"""Tigris brand-isolated replacement diagnostics and opt-in Xiaomi ad cleanup.

inspect/plan: read-only. apply-ads: explicit package approvals + exact firmware
fingerprint + recovery acknowledgement; never touches vendor HOME or core packages.
restore-ads: re-enables ONLY packages recorded as enabled before this operation.

Full HOME takeover still uses install.sh / check-install.py, and a *physical* TV
must verify wake, restart, HOME and recovery before claiming full replacement.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone

from replacement_profiles import CORE_DENY, detect

HOME_INTENT = ("-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
SERIAL_RE = re.compile(r"^[a-zA-Z0-9._:-]+$")


class Failure(RuntimeError):
    pass


def package_list(s: str) -> set[str]:
    return {line.removeprefix("package:").strip() for line in s.splitlines()
            if line.startswith("package:")}


def report(data):
    print(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True))


class Tv:
    def __init__(self, serial: str, runner=subprocess.run):
        if not SERIAL_RE.fullmatch(serial):
            raise Failure("Invalid ADB serial")
        self.serial = serial
        self.runner = runner
        self.evidence = []

    def adb(self, *args):
        command = ["adb", "-s", self.serial, "shell", *args]
        try:
            result = self.runner(command, capture_output=True, text=True,
                                 encoding="utf-8", errors="replace", timeout=25)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise Failure(f"ADB unavailable: {error}") from error
        self.evidence.append({"command": list(args), "exit": result.returncode})
        if result.returncode != 0:
            raise Failure(f"ADB {' '.join(args)} failed: {result.stderr.strip()[:300]}")
        return result.stdout.strip()

    def device(self):
        keys = {"manufacturer": "ro.product.manufacturer", "brand": "ro.product.brand",
                "model": "ro.product.model", "firmware": "ro.build.version.incremental",
                "fingerprint": "ro.build.fingerprint", "sdk": "ro.build.version.sdk"}
        device = {key: self.adb("getprop", prop) for key, prop in keys.items()}
        if not device["sdk"].isdigit() or int(device["sdk"]) < 28:
            raise Failure("Android 9+ required; other OSes use separate platform installers")
        if not all(device[k] for k in ("manufacturer", "brand", "model", "fingerprint")):
            raise Failure("Missing device identity; cannot safely select a brand policy")
        return device

    def inspect(self):
        device = self.device()
        profile = detect(device["manufacturer"], device["brand"])
        installed = package_list(self.adb("pm", "list", "packages"))
        disabled = package_list(self.adb("pm", "list", "packages", "-d"))
        resolved = self.adb("cmd", "package", "resolve-activity", "--brief", *HOME_INTENT)
        candidates = self.adb("cmd", "package", "query-activities", "--brief", *HOME_INTENT)
        diagnostics = {}
        tigris_installed = "com.leslie.tvhome" in installed
        if profile.name == "xiaomi":
            diagnostics["patchwall_installed"] = "com.mitv.tvhome" in installed
            diagnostics["patchwall_disabled"] = "com.mitv.tvhome" in disabled
            diagnostics["android_settings_installed"] = "com.android.tv.settings" in installed
            diagnostics["android_settings_disabled"] = "com.android.tv.settings" in disabled
            # Query the real firmware. Presence of this component does not prove
            # it wins default HOME resolution or that the whitelist exists.
            if "com.android.tv.settings" in installed:
                details = self.adb("dumpsys", "package", "com.android.tv.settings")
                diagnostics["fallbackhome_named_in_manifest_dump"] = "FallbackHome" in details
            diagnostics["home_allowlist"] = "unverified"
            diagnostics["home_collision"] = "unverified"
            diagnostics["system_ads"] = "unverified"
        ads = [{"package": p, "installed": p in installed, "disabled": p in disabled,
                "prevalidated_on_this_firmware": False} for p in profile.ad_candidates]
        return {
            "schema": 1, "device": device, "profile": profile.name,
            "goal": {"home": "Tigris replaces stock Home", "ads": "remove firmware ads where safe",
                     "essential_tv_functions": "preserved", "rollback": "required"},
            "home": {"resolved": resolved, "candidates": candidates,
                     "physical_home": "unverified", "boot": "unverified",
                     "standby_wake": "unverified", "memory_cleanup": "unverified"},
            "tigris_installed": tigris_installed,
            "ad_candidates": ads, "diagnostics": diagnostics,
            "status": "not_fully_verified", "note": profile.note,
            "read_only": True,
            "evidence": self.evidence,
        }


def state_path(directory, serial):
    # Require directory per device; do not reuse or overwrite existing snapshots.
    folder = Path(directory).expanduser() if directory else Path.home() / ".local" / "state" / "tigris" / serial
    return folder / "replacement.json"


def save_state(path: Path, data, *, initial=False):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path.parent, 0o700)
    if initial:
        fd = os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        return
    tmp = path.with_suffix(".tmp")
    fd = os.open(str(tmp), os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    os.replace(tmp, path)


def apply_ads(tv, inspection, approvals, confirm, acknowledge, path):
    profile = detect(inspection["device"]["manufacturer"], inspection["device"]["brand"])
    device = inspection["device"]
    if profile.name != "xiaomi":
        raise Failure("Only Xiaomi/Redmi has a candidate ad policy; no changes for other brands")
    if not inspection["tigris_installed"]:
        raise Failure("Install official Tigris first; refusing ad changes without the replacement desktop")
    if not acknowledge or confirm != device["fingerprint"]:
        raise Failure("Needs --acknowledge-recovery and exact --confirm-fingerprint from inspect")
    selected = tuple(dict.fromkeys(approvals))
    if not selected:
        raise Failure("No ads selected; --package must name individually approved candidates")
    if set(selected) - set(profile.ad_candidates) or set(selected) & CORE_DENY:
        raise Failure("Unsafe or unrecognized system package requested; action blocked")
    present = {item["package"]: item for item in inspection["ad_candidates"]}
    if any(not present[name]["installed"] for name in selected):
        raise Failure("One or more selected packages are not installed; no changes")
    if path.exists():
        previous = json.loads(path.read_text(encoding="utf-8"))
        if previous.get("state") != "restored":
            raise Failure(f"Recovery snapshot already exists: {path}. Restore before another apply")
        # Preserve history instead of overwriting the last successful rollback.
        archive = path.parent / ("replacement-restored-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%f") + ".json")
        os.replace(path, archive)
    planned = [name for name in selected if not present[name]["disabled"]]
    data = {"schema": 1, "serial": tv.serial, "device": device,
            "profile": "xiaomi", "selected": list(selected), "restore_enabled": planned,
            "operation": "apply-ads", "state": "pending",
            "created_at": datetime.now(timezone.utc).isoformat(),
            "warning": "System package disable is firmware-sensitive. Home/boot/ad-free are not verified."}
    # Snapshot the original state before touching even one package.
    save_state(path, data, initial=True)
    try:
        for pkg in planned:
            tv.adb("pm", "disable-user", "--user", "0", pkg)
            if pkg not in package_list(tv.adb("pm", "list", "packages", "-d")):
                raise Failure(f"Package did not become disabled: {pkg}")
        data["state"] = "applied"
        save_state(path, data)
        return {"result": "ads_disabled_partially", "selected": list(selected),
                "changed": planned, "snapshot": str(path),
                "system_ad_free": "unverified", "home_replaced": "unverified",
                "requires": "TV functions, reboot, Home, standby, ad surfaces + physical remote testing"}
    except Exception:
        # Roll back every candidate that was originally enabled. A command may
        # have taken effect even when its output was lost. Never enable a package
        # that was already disabled before this session.
        for pkg in reversed(planned):
            try:
                tv.adb("pm", "enable", "--user", "0", pkg)
            except Failure:
                pass
        data["state"] = "failed-rollback-check-needed"
        save_state(path, data)
        raise


def restore_ads(tv, path, firmware_change=False):
    if not path.exists():
        raise Failure(f"No recovery snapshot at {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    device = tv.device()
    if data.get("serial") != tv.serial or data.get("profile") != "xiaomi" or data.get("schema") != 1:
        raise Failure("Snapshot identity mismatch")
    prior = data["device"]
    if (device["model"], device["manufacturer"], device["brand"]) != (
            prior["model"], prior["manufacturer"], prior["brand"]):
        raise Failure("Different TV detected; refusing restore")
    if device["fingerprint"] != prior["fingerprint"] and not firmware_change:
        raise Failure("Firmware fingerprint changed; review before using --allow-firmware-change")
    changed = data.get("restore_enabled", [])
    if len(changed) != len(set(changed)) or any(
            pkg not in detect(prior["manufacturer"], prior["brand"]).ad_candidates
            or pkg in CORE_DENY for pkg in changed):
        raise Failure("Snapshot includes unapproved package")
    errors = []
    for pkg in reversed(changed):
        try:
            tv.adb("pm", "enable", "--user", "0", pkg)
        except Failure as error:
            errors.append(str(error))
    disabled = package_list(tv.adb("pm", "list", "packages", "-d"))
    errors.extend(f"Still disabled: {pkg}" for pkg in changed if pkg in disabled)
    if errors:
        data["state"] = "restore-incomplete"
        save_state(path, data)
        raise Failure("; ".join(errors))
    data["state"] = "restored"
    data["restored_at"] = datetime.now(timezone.utc).isoformat()
    save_state(path, data)
    return {"result": "restored", "packages": changed, "snapshot": str(path),
            "home": "unchanged", "reboot": "unverified"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("inspect", "plan", "apply-ads", "restore-ads"))
    parser.add_argument("--serial", required=True, help="ADB device serial, typically 192.168.x.x:5555")
    parser.add_argument("--state-dir", help="Per-device recovery state directory")
    parser.add_argument("--package", action="append", default=[], help="One individually approved ad package (repeat)")
    parser.add_argument("--confirm-fingerprint", help="Exact ro.build.fingerprint, obtained from inspect")
    parser.add_argument("--acknowledge-recovery", action="store_true",
                        help="I understand that recovery may need working ADB after a crash/reboot")
    parser.add_argument("--allow-firmware-change", action="store_true")
    args = parser.parse_args()
    try:
        tv = Tv(args.serial)
        path = state_path(args.state_dir, args.serial)
        if args.mode == "restore-ads":
            report(restore_ads(tv, path, args.allow_firmware_change))
            return 0
        inspected = tv.inspect()
        if args.mode == "inspect":
            report(inspected)
        elif args.mode == "plan":
            inspected["mode"] = "plan"
            inspected["recommended_order"] = [
                "Install official signed Tigris via install.sh",
                "Verify real HOME resolution with check-install.py and physical remote",
                "Review device-specific ad candidates, preserve system settings and original HOME",
                "Obtain explicit per-package approval and test recoverability before disabling",
                "Recheck system functions, reboot, wake and physical HOME; report unverified separately",
            ]
            inspected["commands_are_not_executed"] = True
            report(inspected)
        else:
            report(apply_ads(tv, inspected, args.package, args.confirm_fingerprint,
                             args.acknowledge_recovery, path))
        return 0
    except (Failure, OSError, ValueError, json.JSONDecodeError) as error:
        report({"result": "blocked", "reason": str(error), "changes_unverified": True})
        return 2


if __name__ == "__main__":
    sys.exit(main())
