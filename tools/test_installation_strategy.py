import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from contextlib import redirect_stdout
import io

from installation_strategy import registry, select

SPEC = importlib.util.spec_from_file_location("install_plan", Path(__file__).with_name("install-plan.py"))
installer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(installer)


class StrategyTests(unittest.TestCase):
    def device(self, brand="Sony", **overrides):
        return dict(dict(manufacturer=brand, brand=brand, model="fixture", sdk="28",
                         fingerprint="fixture/unknown", firmware="fixture"), **overrides)

    def test_vendor_routes(self):
        for brand, expected in [("Sony", "sony-native"), ("TCL", "tcl-compatible"),
                                ("FFalcon", "tcl-compatible"), ("Redmi", "xiaomi-diagnostic"),
                                ("Xiaomi", "xiaomi-diagnostic"), ("Other", "generic-android")]:
            with self.subTest(brand=brand):
                plan = select(self.device(brand))
                self.assertEqual(plan["strategy"]["id"], expected)
                self.assertFalse(plan["hardware_verified"])
                self.assertEqual(set(plan["results"].values()), {"unverified"})

    def test_miui_hyperos_labels_do_not_authorize_mutations(self):
        for firmware in ("MIUI-TV-1.5", "HyperOS-TV-2", "unknown"):
            plan = select(self.device("Xiaomi", firmware=firmware))
            self.assertEqual(plan["strategy"]["executor"], "apk-only")
            self.assertEqual(plan["automatic_package_changes"], [])

    def test_cli_non_android_never_calls_adb(self):
        with patch("sys.argv", ["install-plan.py", "--platform", "webos", "--execute"]), \
                patch.object(installer.subprocess, "run") as run, redirect_stdout(io.StringIO()):
            self.assertEqual(installer.main(), 2)
            run.assert_not_called()

    def test_cli_disconnected_unauthorized_never_installs(self):
        for state in ("unauthorized", "offline", ""):
            with patch("sys.argv", ["install-plan.py", "--platform", "android", "--serial",
                                    "192.168.1.20:5555", "--execute"]), \
                    patch.object(installer.subprocess, "run", return_value=SimpleNamespace(
                        returncode=0, stdout=state)) as run, redirect_stdout(io.StringIO()):
                self.assertEqual(installer.main(), 2)
                self.assertEqual(run.call_count, 2)
                self.assertTrue(all(c.args[0][0] == "adb" for c in run.call_args_list))

    def test_unknown_and_conflicting_firmware_fail_closed(self):
        rows = registry()
        rows.append(dict(rows[0], id="competing-sony"))
        self.assertEqual(select(self.device(), strategies=rows)["reason"], "conflicting_rules")
        rows[-1]["fingerprints"] = ["other/firmware"]
        self.assertEqual(select(self.device(), strategies=rows)["strategy"]["id"], "sony-native")
        self.assertEqual(select(self.device(fingerprint=""))["status"], "diagnostic_only")

    def test_non_android_and_old_android(self):
        for platform in ("webos", "tizen", "unknown"):
            self.assertEqual(select({}, platform)["status"], "blocked")
        for sdk in ("27", "", "unknown"):
            self.assertEqual(select(self.device(sdk=sdk))["status"], "blocked")

    def test_conflicting_manufacturer_brand_blocks(self):
        self.assertEqual(select(self.device(brand="Xiaomi", manufacturer="Sony"))["status"],
                         "diagnostic_only")

    def test_executor_preserves_legacy_and_uses_safe_xiaomi_path(self):
        for brand in ("Sony", "TCL", "Xiaomi", "Other"):
            calls = []
            def run(args, **kwargs):
                calls.append((args, kwargs))
                return SimpleNamespace(returncode=7)
            self.assertEqual(installer.execute(select(self.device(brand)), "192.168.1.20:5555", run), 7)
            args, kwargs = calls[0]
            self.assertEqual("--apk-only" in args, brand in ("Xiaomi", "Other"))
            self.assertEqual(kwargs["env"]["TV"], "192.168.1.20:5555")
            self.assertNotIn("--uninstall", args)

    def test_blocked_plan_and_injection_never_execute(self):
        def never(*args, **kwargs):
            self.fail("must not execute")
        with self.assertRaises(ValueError):
            installer.execute(select({}, "webos"), "192.168.1.20", never)
        with self.assertRaises(ValueError):
            installer.execute(select(self.device()), "192.168.1.20;reboot", never)

    def test_registry_contract_and_locale_parity(self):
        required = {"id", "platform", "brands", "fingerprints", "preconditions", "detection",
                    "executor", "apk", "home", "verification", "failure", "recovery", "risk",
                    "evidence", "validation"}
        for row in registry():
            self.assertFalse(required - row.keys())
            for key in ("name", "guidance"):
                self.assertEqual(set(row[key]), {"zh", "en"})


if __name__ == "__main__":
    unittest.main()
