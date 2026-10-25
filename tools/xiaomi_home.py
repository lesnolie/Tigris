"""Xiaomi/Redmi HOME routing diagnostics and reversible, non-destructive trial.

This module NEVER disables, uninstalls, clears or hides the stock launcher,
FallbackHome, settings, or any other package. A trial changes only Android's
default HOME preference, observes resolver and ADB HOME behavior, then restores
the exact originally resolved component even when the trial succeeds.

A successful trial is *not* firmware certification: physical HOME, cold boot,
wake, memory cleanup, vendor advertising and rollback require device testing.
"""
import json
import re
import time
from datetime import datetime, timezone

TIGRIS_HOME = "com.leslie.tvhome/.HomeActivity"
TIGRIS_PACKAGE = "com.leslie.tvhome"
PATCHWALL = "com.mitv.tvhome"
COMPONENT = re.compile(r"(?<![\w.])([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)(?![\w.])")
HOME_ARGS = ("-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")


def parse_component(text):
    """Prefer the sole HOME component in 'resolve-activity --brief' output."""
    parts = list(dict.fromkeys(COMPONENT.findall(text or "")))
    return parts[0] if len(parts) == 1 else None


def full_component(component):
    if not component or "/" not in component:
        return None
    package, activity = component.split("/", 1)
    return package + "/" + (package + activity if activity.startswith(".") else activity)


def component_package(component):
    return component.split("/", 1)[0] if component else None


def focus(window_text):
    found = re.findall(r"mCurrentFocus[^\n]*?\b([\w.]+/[\w.$]+)", window_text or "")
    values = {full_component(s) for s in found}
    return values.pop() if len(values) == 1 else None


def inspect_route(snapshot):
    """Conservative explanation only; no allowlist claims from resolver alone."""
    profile = snapshot.get("profile")
    home = snapshot.get("home", {})
    resolved = parse_component(home.get("resolved"))
    candidates = home.get("candidates", "")
    diag = snapshot.get("diagnostics", {})
    raw_guard = diag.get("enabled_accessibility_services", "")
    guard_active = "com.leslie.tvhome.HomeGuardService" in raw_guard
    tigris_candidate = "com.leslie.tvhome/" in candidates or (
        "com.leslie.tvhome.HomeActivity" in candidates)
    if profile != "xiaomi":
        classification = "different_vendor"
    elif not snapshot.get("tigris_installed"):
        classification = "tigris_not_installed"
    elif not tigris_candidate:
        classification = "tigris_home_not_visible"
    elif not resolved:
        classification = "unknown_or_ambiguous_resolver"
    elif component_package(resolved) == TIGRIS_PACKAGE:
        classification = "tigris_resolves_native_candidate"
    elif component_package(resolved) == PATCHWALL:
        classification = "patchwall_selected"
    elif "FallbackHome" in resolved:
        classification = "fallbackhome_selected"
    else:
        classification = "other_home_selected"
    return {
        "classification": classification,
        "resolved_component": resolved,
        "tigris_home_candidate": bool(tigris_candidate),
        "guard_in_enabled_accessibility_list": guard_active,
        "allowlist_proven": False,
        "physical_home_verified": False,
        "action": "no_system_package_changes",
    }


def home_resolver(tv):
    return parse_component(tv.adb("cmd", "package", "resolve-activity",
                                  "--brief", *HOME_ARGS))


def test_adb_home(tv, pause=time.sleep):
    """Only a supplemental synthetic HOME check; never physical key evidence."""
    target = full_component(TIGRIS_HOME)
    # Use Xiaomi's OEM settings screen where available, otherwise the standard
    # Android settings action. If neither works, fail closed without pressing HOME.
    started = False
    for intent in (
        ("am", "start", "-W", "-n",
         "com.xiaomi.mitv.settings/com.xiaomi.mitv.settings.entry.MainActivity"),
        ("am", "start", "-W", "-a", "android.settings.SETTINGS"),
    ):
        try:
            output = tv.adb(*intent)
            if re.search(r"Error|Exception|does not exist|not found", output, re.I):
                continue
            pause(.4)
            base = focus(tv.adb("dumpsys", "window"))
            if base and base != target:
                started = True
                break
        except Exception:
            continue
    if not started:
        return {"result": "unverified", "reason": "cannot_start_separate_settings_window"}
    try:
        tv.adb("input", "keyevent", "KEYCODE_HOME")
        observations = []
        for _ in range(5):
            pause(.6)
            observations.append(focus(tv.adb("dumpsys", "window")))
    except Exception:
        return {"result": "unverified", "reason": "adb_home_check_failed"}
    if all(v == target for v in observations[-3:]):
        return {"result": "passed", "observations": observations}
    if all(v is not None for v in observations[-3:]):
        return {"result": "failed", "observations": observations}
    return {"result": "unverified", "observations": observations}


def probe_home(tv, snapshot, fingerprint, acknowledged, path, save_state,
               pause=time.sleep, keep_if_passed=False):
    """Trial a standard HOME preference, then restore in finally.

    No OEM package disable. Even a trial that appears successful *always*
    restores the previous native HOME. Requires a saved, unambiguous stock
    resolver and inactive Tigris accessibility guard to prevent false evidence.
    """
    from replacement_profiles import detect
    device = snapshot["device"]
    if detect(device["manufacturer"], device["brand"]).name != "xiaomi":
        raise ValueError("HOME trial is Xiaomi/Redmi only")
    if not snapshot.get("tigris_installed"):
        raise ValueError("Tigris not installed; use the Standard flavor first")
    if not acknowledged or fingerprint != device["fingerprint"]:
        raise ValueError("Confirm current fingerprint and acknowledge physical recovery risk")
    state = inspect_route(snapshot)
    if state["guard_in_enabled_accessibility_list"]:
        raise ValueError("Turn off Tigris Home Guard before native-route trial; avoid fake positive")
    if not state["tigris_home_candidate"]:
        raise ValueError("Tigris HOME Activity is not an installed HOME candidate (Guard flavor?)")
    original = state["resolved_component"]
    if component_package(original) != PATCHWALL:
        raise ValueError("Safe trial requires an unambiguous currently resolved PatchWall HOME")
    if snapshot["diagnostics"].get("patchwall_disabled"):
        raise ValueError("PatchWall already disabled; restore manually before probing")
    if path.exists():
        prior = json.loads(path.read_text(encoding="utf-8"))
        if prior.get("state") != "restored":
            raise ValueError("Previous HOME trial needs recovery first; snapshot preserved")
        # Preserve the old report and its rollback evidence when probing again.
        suffix = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%f")
        path.rename(path.with_name("home-probe-restored-" + suffix + ".json"))
    payload = {
        "schema": 1, "operation": "home-trial", "state": "pending",
        "serial": tv.serial, "device": device, "original": original,
        "target": TIGRIS_HOME, "no_system_components_disabled": True,
        "physical_home": "unverified", "boot": "unverified", "standby": "unverified",
    }
    save_state(path, payload, initial=True)
    outcome = {"result": "unverified"}
    issue = None
    retained = False
    try:
        tv.adb("cmd", "package", "set-home-activity", TIGRIS_HOME)
        payload["state"] = "trial-in-progress"
        save_state(path, payload)
        selected = home_resolver(tv)
        outcome["resolver"] = selected
        # If OEM Home still wins, skip key input (risk of landing in FallbackHome).
        if component_package(selected) == TIGRIS_PACKAGE:
            outcome["adb_home"] = test_adb_home(tv, pause)
        else:
            outcome["adb_home"] = {"result": "not_attempted",
                                   "reason": "Tigris_not_selected_by_resolver"}
        outcome["result"] = (
            "native_home_trial_candidate"
            if (component_package(selected) == TIGRIS_PACKAGE
                and outcome["adb_home"]["result"] == "passed")
            else "native_home_not_proven"
        )
        retained = keep_if_passed and outcome["result"] == "native_home_trial_candidate"
    except Exception as error:
        issue = error
        outcome["result"] = "trial_failed"
        outcome["error"] = str(error)
    finally:
        # Always attempt restoration, even if initial set-home succeeds then
        # connection fails. The snapshot remains if restoration cannot be proven.
        if retained:
            outcome["original_restored_by_resolver"] = False
            outcome["retained_native_preference"] = True
            payload["state"] = "active_pending_physical_verification"
        else:
            payload["state"] = "restoring"
            save_state(path, payload)
            try:
                tv.adb("cmd", "package", "set-home-activity", original)
                restored = home_resolver(tv)
                outcome["original_restored_by_resolver"] = (
                    full_component(restored) == full_component(original))
            except Exception as error:
                outcome["original_restored_by_resolver"] = False
                outcome["restore_error"] = str(error)
            payload["state"] = (
                "restored" if outcome["original_restored_by_resolver"]
                else "restore_needs_attention"
            )
        payload["outcome"] = outcome
        save_state(path, payload)
    if not retained and not outcome["original_restored_by_resolver"]:
        raise RuntimeError(
            "Original HOME NOT VERIFIED RESTORED. Use full-home-restore with "
            f"snapshot {path}; original: {original}")
    if issue:
        raise RuntimeError(f"Trial failed (original HOME restored): {issue}") from issue
    return dict(outcome, original=original, snapshot=str(path),
                physical_home="unverified", cold_boot="unverified",
                home_persistent=("pending_boot_and_wake_validation" if retained
                                 else "not_enabled_by_trial"),
                restore_command="full-home-restore")


def restore_home(tv, path, save_state, *, allow_firmware_change=False):
    if not path.exists():
        raise ValueError("No saved HOME trial to restore")
    payload = json.loads(path.read_text(encoding="utf-8"))
    device = tv.device()
    original = payload.get("original")
    before = payload.get("device", {})
    if payload.get("operation") != "home-trial" or payload.get("schema") != 1:
        raise ValueError("Unexpected snapshot format")
    if payload.get("serial") != tv.serial or any(
        before.get(k) != device.get(k) for k in ("manufacturer", "brand", "model")
    ):
        raise ValueError("Different TV; refusing to apply HOME recovery")
    if before.get("fingerprint") != device.get("fingerprint") and not allow_firmware_change:
        raise ValueError("Firmware changed; manual confirmation required")
    if component_package(original) != PATCHWALL or not full_component(original):
        raise ValueError("Invalid stored original HOME")
    tv.adb("cmd", "package", "set-home-activity", original)
    selected = home_resolver(tv)
    okay = full_component(selected) == full_component(original)
    payload["state"] = "restored" if okay else "restore_needs_attention"
    save_state(path, payload)
    if not okay:
        raise RuntimeError("HOME restore sent but resolver still differs; investigate before retry")
    return {"result": "restored_by_resolver", "original": original,
            "physical_home": "unverified", "snapshot": str(path)}
