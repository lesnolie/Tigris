"""Offline Xiaomi/Redmi native HOME trial and recovery tests (no real ADB)."""
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from xiaomi_home import (
    TIGRIS_HOME, inspect_route, parse_component, full_component,
    probe_home, restore_home,
)

def save(path, data, initial=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x" if initial else "w", encoding="utf-8") as f:
        json.dump(data, f)

def inspection(vendor="Xiaomi", guard=False, installed=True, visible=True):
    return {
        "device": {"manufacturer": vendor, "brand": vendor, "model": "MiTV-ASTP0",
                   "fingerprint": "fake/miui/9", "sdk": "28"},
        "profile": "xiaomi" if vendor == "Xiaomi" else "sony",
        "tigris_installed": installed,
        "home": {
            "resolved": "priority=0\ncom.mitv.tvhome/.MainActivity",
            "candidates": ("com.mitv.tvhome/.MainActivity\n"
                           + ("com.leslie.tvhome/.HomeActivity" if visible else "")),
        },
        "diagnostics": {
            "patchwall_disabled": False,
            "enabled_accessibility_services": (
                "com.leslie.tvhome/com.leslie.tvhome.HomeGuardService" if guard
                else "com.other/.Service"),
        },
    }

class FakeTV:
    serial = "192.168.1.20:5555"
    def __init__(self, pinned=False, restore_failure=False, fail_probe=False):
        self.current = "com.mitv.tvhome/.MainActivity"
        self.calls = []
        self.pinned = pinned
        self.restore_failure = restore_failure
        self.fail_probe = fail_probe
        self.foreground = "com.mitv.tvhome/.MainActivity"

    def adb(self, *args):
        self.calls.append(args)
        if args[:3] == ("cmd", "package", "resolve-activity"):
            return "priority=0\n" + self.current
        if args[:3] == ("cmd", "package", "set-home-activity"):
            target = args[-1]
            if target.startswith("com.mitv.tvhome") and self.restore_failure:
                raise OSError("ADB disconnected before restoration")
            if not self.pinned:
                self.current = target
            return "Success"
        if args[:2] == ("am", "start"):
            if self.fail_probe:
                raise OSError("Settings not reachable")
            self.foreground = "com.xiaomi.mitv.settings/.entry.MainActivity"
            return "Status: ok"
        if args[:2] == ("dumpsys", "window"):
            return "mCurrentFocus=Window{12 u0 " + self.foreground + "}"
        if args[:2] == ("input", "keyevent"):
            self.foreground = (
                TIGRIS_HOME if self.current.startswith("com.leslie.tvhome")
                else self.current)
            return ""
        raise AssertionError(args)

    def device(self):
        return inspection()["device"]

    def writes(self):
        return [args for args in self.calls
                if args[:3] == ("cmd", "package", "set-home-activity")
                or args[:2] == ("pm", "disable-user")
                or args[:2] == ("pm", "uninstall")]

class XiaomiHomeTests(unittest.TestCase):
    def test_parse_activity_from_resolver_not_match_score(self):
        self.assertEqual(parse_component("priority=0 match=0x108000\n"
                         "com.mitv.tvhome/.MainActivity"),
                         "com.mitv.tvhome/.MainActivity")
        self.assertIsNone(parse_component("com.foo/.A\ncom.bar/.B"))
        self.assertIsNone(parse_component("No activity found"))
        self.assertEqual(full_component("com.mitv.tvhome/.A"),
                         "com.mitv.tvhome/com.mitv.tvhome.A")

    def test_classification_without_making_home_allowlist_claims(self):
        result = inspect_route(inspection())
        self.assertEqual(result["classification"], "patchwall_selected")
        self.assertFalse(result["allowlist_proven"])
        self.assertFalse(result["guard_in_enabled_accessibility_list"])
        x = inspection(visible=False)
        self.assertEqual(inspect_route(x)["classification"], "tigris_home_not_visible")
        x["home"]["resolved"] = "com.android.tv.settings/.system.FallbackHome"
        x["home"]["candidates"] += "\ncom.leslie.tvhome/.HomeActivity"
        self.assertEqual(inspect_route(x)["classification"], "fallbackhome_selected")

    def test_guard_and_other_brands_cannot_probe(self):
        for snap in (inspection(guard=True), inspection(vendor="Sony"),
                     inspection(installed=False), inspection(visible=False)):
            tv = FakeTV()
            with TemporaryDirectory() as temp:
                with self.assertRaises(ValueError):
                    probe_home(tv, snap, snap["device"]["fingerprint"], True,
                               Path(temp) / "home.json", save, lambda _: None)
            self.assertEqual(tv.writes(), [])

    def test_requires_exact_fingerprint_and_explicit_consent(self):
        for token, approve in (("wrong", True), ("fake/miui/9", False)):
            tv = FakeTV()
            with TemporaryDirectory() as temp:
                with self.assertRaises(ValueError):
                    probe_home(tv, inspection(), token, approve,
                               Path(temp) / "home.json", save, lambda _: None)
            self.assertEqual(tv.writes(), [])

    def test_trial_proves_only_adb_candidate_and_restores_original(self):
        tv = FakeTV()
        with TemporaryDirectory() as temp:
            path = Path(temp) / "home.json"
            result = probe_home(tv, inspection(), "fake/miui/9", True,
                                path, save, lambda _: None)
            self.assertEqual(result["result"], "native_home_trial_candidate")
            self.assertEqual(result["adb_home"]["result"], "passed")
            self.assertTrue(result["original_restored_by_resolver"])
            self.assertEqual(result["physical_home"], "unverified")
            self.assertEqual(result["home_persistent"], "not_enabled_by_trial")
            self.assertEqual(tv.current, "com.mitv.tvhome/.MainActivity")
            self.assertEqual(json.loads(path.read_text())["state"], "restored")
            self.assertEqual(len(tv.writes()), 2)
            self.assertFalse(any(args[:1] == ("pm",) for args in tv.calls))

    def test_set_home_success_but_oem_resolver_refuses_change(self):
        tv = FakeTV(pinned=True)
        with TemporaryDirectory() as temp:
            result = probe_home(tv, inspection(), "fake/miui/9", True,
                                Path(temp) / "home.json", save, lambda _: None)
            self.assertEqual(result["result"], "native_home_not_proven")
            self.assertEqual(result["adb_home"]["result"], "not_attempted")
            self.assertTrue(result["original_restored_by_resolver"])

    def test_trial_setting_window_failure_restores_original(self):
        tv = FakeTV(fail_probe=True)
        with TemporaryDirectory() as temp:
            outcome = probe_home(tv, inspection(), "fake/miui/9", True,
                                 Path(temp) / "home.json", save, lambda _: None)
            self.assertEqual(outcome["result"], "native_home_not_proven")
            self.assertEqual(outcome["adb_home"]["result"], "unverified")
            self.assertEqual(tv.current, "com.mitv.tvhome/.MainActivity")

    def test_restore_failure_keeps_rescue_snapshot_and_blocks_retrial(self):
        tv = FakeTV(restore_failure=True)
        with TemporaryDirectory() as temp:
            path = Path(temp) / "home.json"
            with self.assertRaisesRegex(RuntimeError, "NOT VERIFIED RESTORED"):
                probe_home(tv, inspection(), "fake/miui/9", True,
                           path, save, lambda _: None)
            self.assertEqual(json.loads(path.read_text())["state"], "restore_needs_attention")
            self.assertTrue(path.exists())
            with self.assertRaisesRegex(ValueError, "Previous HOME trial"):
                probe_home(FakeTV(), inspection(), "fake/miui/9", True,
                           path, save, lambda _: None)

    def test_explicit_restore_checks_identity_and_firmware(self):
        tv = FakeTV()
        with TemporaryDirectory() as temp:
            path = Path(temp) / "home.json"
            probe_home(tv, inspection(), "fake/miui/9", True,
                       path, save, lambda _: None)
            x = restore_home(tv, path, save)
            self.assertEqual(x["result"], "restored_by_resolver")
            self.assertEqual(x["physical_home"], "unverified")
            tv.device = lambda: dict(inspection()["device"], model="AnotherTV")
            with self.assertRaisesRegex(ValueError, "Different TV"):
                restore_home(tv, path, save)

    def test_activation_is_opt_in_and_restorable(self):
        tv = FakeTV()
        with TemporaryDirectory() as temp:
            path = Path(temp) / "home.json"
            r = probe_home(tv, inspection(), "fake/miui/9", True,
                           path, save, lambda _: None, keep_if_passed=True)
            self.assertTrue(r["retained_native_preference"])
            self.assertEqual(r["physical_home"], "unverified")
            self.assertEqual(tv.current, TIGRIS_HOME)
            self.assertEqual(json.loads(path.read_text())["state"],
                             "active_pending_physical_verification")
            self.assertEqual(restore_home(tv, path, save)["result"], "restored_by_resolver")

    def test_activation_rolls_back_if_oem_routing_refuses_change(self):
        tv = FakeTV(pinned=True)
        with TemporaryDirectory() as temp:
            path = Path(temp) / "home.json"
            r = probe_home(tv, inspection(), "fake/miui/9", True,
                           path, save, lambda _: None, keep_if_passed=True)
            self.assertEqual(r["result"], "native_home_not_proven")
            self.assertEqual(json.loads(path.read_text())["state"], "restored")
            self.assertEqual(tv.current, "com.mitv.tvhome/.MainActivity")

    def test_refuses_trial_if_fallbackhome_or_tigris_is_original(self):
        for target in ("com.android.tv.settings/.system.FallbackHome",
                       TIGRIS_HOME):
            tv = FakeTV()
            snap = inspection()
            snap["home"]["resolved"] = target
            with TemporaryDirectory() as temp:
                with self.assertRaises(ValueError):
                    probe_home(tv, snap, "fake/miui/9", True,
                               Path(temp) / "home.json", save, lambda _: None)
            self.assertEqual(tv.writes(), [])

if __name__ == "__main__":
    unittest.main()
