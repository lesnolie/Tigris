"""Regression tests for brand isolation and reversible Xiaomi ad operations (mock ADB)."""
import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("replacement", Path(__file__).with_name("tv-replacement.py"))
replacement = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(replacement)


class FakeTv:
    def __init__(self, brand="Xiaomi", model="MiTV-ASTP0", fingerprint="demo/firmware/123"):
        self.brand = brand
        self.model = model
        self.fingerprint = fingerprint
        self.installed = {"com.leslie.tvhome", "com.miui.systemAdSolution",
                          "com.mitv.tvad", "com.mitv.tvhome", "com.android.tv.settings"}
        self.disabled = {"com.mitv.tvad"}  # User had disabled this BEFORE Tigris.
        self.calls = []
        self.fail_disable = None

    def __call__(self, argv, **kwargs):
        command = tuple(argv[4:])
        self.calls.append(command)
        text = ""
        code = 0
        if command[:1] == ("getprop",):
            props = {
                "ro.product.manufacturer": self.brand, "ro.product.brand": self.brand,
                "ro.product.model": self.model, "ro.build.version.incremental": "2026.demo",
                "ro.build.fingerprint": self.fingerprint, "ro.build.version.sdk": "28",
                "ro.board.platform": "fixture_board", "ro.hardware": "fixture_hw",
                "ro.product.device": "fixture_device", "ro.product.name": "fixture_product",
            }
            text = props[command[1]]
        elif command == ("pm", "list", "packages"):
            text = "\n".join("package:" + pkg for pkg in sorted(self.installed))
        elif command == ("pm", "list", "packages", "-d"):
            text = "\n".join("package:" + pkg for pkg in sorted(self.disabled))
        elif command[:2] == ("cmd", "package"):
            text = "com.mitv.tvhome/.MainActivity"
        elif command[:2] == ("dumpsys", "package"):
            text = "com.android.tv.settings.system.FallbackHome"
        elif command == ("settings", "get", "secure", "enabled_accessibility_services"):
            text = "com.other/.Service"
        elif command[:3] == ("pm", "disable-user", "--user"):
            pkg = command[-1]
            if pkg == self.fail_disable:
                code = 1
            else:
                self.disabled.add(pkg)
                text = "new state: disabled-user"
        elif command[:3] == ("pm", "enable", "--user"):
            self.disabled.discard(command[-1])
            text = "new state: enabled"
        else:
            raise AssertionError(f"Unexpected ADB operation {command}")
        return SimpleNamespace(returncode=code, stdout=text, stderr="fake failure" if code else "")

    def writes(self):
        return [cmd for cmd in self.calls if cmd[:2] == ("pm", "disable-user")
                or cmd[:2] == ("pm", "enable")]


class ReplacementTests(unittest.TestCase):
    def setUp(self):
        # Synthetic reviewed firmware only; never add this to the shipped policy.
        self.policy = patch.dict("replacement_profiles.VERIFIED_AD_FIRMWARES", {
            "demo/firmware/123": ("com.miui.systemAdSolution", "com.mitv.tvad")})
        self.policy.start()
        self.addCleanup(self.policy.stop)

    def test_unknown_firmware_cannot_disable_ads_even_with_consent(self):
        fake = FakeTv(fingerprint="unreviewed/firmware")
        tv = self.tv(fake)
        with TemporaryDirectory() as d:
            with self.assertRaisesRegex(replacement.Failure, "exact-firmware"):
                replacement.apply_ads(tv, tv.inspect(), ["com.miui.systemAdSolution"],
                                      fake.fingerprint, True, Path(d)/"replacement.json")
        self.assertEqual(fake.writes(), [])

    def tv(self, fake):
        return replacement.Tv("192.168.1.23:5555", fake)

    def test_xiaomi_profile_is_strictly_vendor_scoped(self):
        from replacement_profiles import detect
        self.assertEqual(detect("Xiaomi", "Redmi").name, "xiaomi")
        self.assertEqual(detect("Sony", "BRAVIA").name, "sony")
        self.assertEqual(detect("TCL", "FFALCON").name, "tcl")
        self.assertEqual(detect("Skyworth", "coocaa").name, "coocaa")
        self.assertEqual(detect("Hisense", "VIDAA").name, "hisense")
        self.assertEqual(detect("Unknown", "Android").ad_candidates, ())

    def test_inspect_is_read_only_and_reports_unknown_status(self):
        fake = FakeTv()
        result = self.tv(fake).inspect()
        self.assertEqual(result["profile"], "xiaomi")
        self.assertEqual(result["home"]["physical_home"], "unverified")
        self.assertEqual(result["diagnostics"]["home_allowlist"], "unverified")
        self.assertTrue(result["diagnostics"]["fallbackhome_named_in_manifest_dump"])
        self.assertEqual(fake.writes(), [])

    def test_other_brands_cannot_apply_xiaomi_ads(self):
        fake = FakeTv(brand="Sony")
        tv = self.tv(fake)
        with TemporaryDirectory() as d:
            with self.assertRaises(replacement.Failure):
                replacement.apply_ads(tv, tv.inspect(), ["com.miui.systemAdSolution"],
                    fake.fingerprint, True, Path(d)/"replacement.json")
        self.assertEqual(fake.writes(), [])

    def test_missing_acknowledgement_or_wrong_fingerprint_blocks(self):
        fake = FakeTv()
        tv = self.tv(fake)
        with TemporaryDirectory() as d:
            for token, ack in (("", True), (fake.fingerprint, False)):
                with self.assertRaises(replacement.Failure):
                    replacement.apply_ads(tv, tv.inspect(), ["com.miui.systemAdSolution"],
                        token, ack, Path(d)/"replacement.json")
        self.assertEqual(fake.writes(), [])

    def test_core_packages_and_unknown_packages_are_never_disabled(self):
        fake = FakeTv()
        tv = self.tv(fake)
        with TemporaryDirectory() as d:
            for pkg in ("com.mitv.tvhome", "com.android.tv.settings",
                        "com.xiaomi.mitv.settings", "com.mitv.tvplayer", "com.fake.ads"):
                with self.assertRaises(replacement.Failure):
                    replacement.apply_ads(tv, tv.inspect(), [pkg],
                        fake.fingerprint, True, Path(d)/"replacement.json")
        self.assertEqual(fake.writes(), [])

    def test_apply_and_restore_only_previous_enabled_packages(self):
        fake = FakeTv()
        tv = self.tv(fake)
        with TemporaryDirectory() as d:
            path = Path(d)/"replacement.json"
            result = replacement.apply_ads(tv, tv.inspect(),
                ["com.miui.systemAdSolution", "com.mitv.tvad"], fake.fingerprint, True, path)
            self.assertEqual(result["changed"], ["com.miui.systemAdSolution"])
            self.assertIn("com.miui.systemAdSolution", fake.disabled)
            self.assertIn("com.mitv.tvad", fake.disabled)
            self.assertEqual(result["system_ad_free"], "unverified")
            self.assertTrue(path.exists())
            restored = replacement.restore_ads(tv, path)
            self.assertEqual(restored["result"], "restored")
            self.assertNotIn("com.miui.systemAdSolution", fake.disabled)
            self.assertIn("com.mitv.tvad", fake.disabled)  # User's prior state remains.

    def test_existing_snapshot_prevents_overwrite(self):
        fake = FakeTv()
        tv = self.tv(fake)
        with TemporaryDirectory() as d:
            path = Path(d)/"replacement.json"
            replacement.apply_ads(tv, tv.inspect(), ["com.miui.systemAdSolution"], fake.fingerprint, True, path)
            with self.assertRaises(replacement.Failure):
                replacement.apply_ads(tv, tv.inspect(), ["com.miui.systemAdSolution"], fake.fingerprint, True, path)

    def test_failed_operation_attempts_rollback_and_keeps_snapshot(self):
        fake = FakeTv()
        fake.fail_disable = "com.mitv.tvad"
        # Explicitly mark it enabled, so it is part of this transaction.
        fake.disabled.remove("com.mitv.tvad")
        tv = self.tv(fake)
        with TemporaryDirectory() as d:
            path = Path(d)/"replacement.json"
            with self.assertRaises(replacement.Failure):
                replacement.apply_ads(tv, tv.inspect(),
                    ["com.miui.systemAdSolution", "com.mitv.tvad"], fake.fingerprint, True, path)
            self.assertNotIn("com.miui.systemAdSolution", fake.disabled)
            self.assertEqual(__import__("json").loads(path.read_text())["state"], "failed-rollback-check-needed")

    def test_restore_checks_device_and_firmware(self):
        fake = FakeTv()
        tv = self.tv(fake)
        with TemporaryDirectory() as d:
            path = Path(d)/"replacement.json"
            replacement.apply_ads(tv, tv.inspect(), ["com.miui.systemAdSolution"], fake.fingerprint, True, path)
            fake.fingerprint = "new/firmware"
            with self.assertRaises(replacement.Failure):
                replacement.restore_ads(tv, path)
            self.assertEqual(replacement.restore_ads(tv, path, firmware_change=True)["result"], "restored")

    def test_no_script_string_interpolation_or_vendor_launcher_mutation(self):
        fake = FakeTv()
        tv = self.tv(fake)
        with TemporaryDirectory() as d:
            path = Path(d)/"replacement.json"
            replacement.apply_ads(tv, tv.inspect(), ["com.miui.systemAdSolution"], fake.fingerprint, True, path)
            self.assertFalse(any(cmd[-1] in ("com.mitv.tvhome", "com.android.tv.settings")
                                 for cmd in fake.writes()))
            self.assertFalse(any(cmd[0] in ("sh", "su") for cmd in fake.calls))


if __name__ == "__main__":
    unittest.main()
