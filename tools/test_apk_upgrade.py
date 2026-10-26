import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("apk_upgrade", Path(__file__).with_name("verify-apk-upgrade.py"))
upgrade = importlib.util.module_from_spec(spec)
spec.loader.exec_module(upgrade)


class ApkUpgradeTests(unittest.TestCase):
    def setUp(self):
        self.old = dict(package="com.leslie.tvhome", code=14, version="2.0.8", min_sdk=28,
                        target_sdk=31, abis=set(), signers={upgrade.STABLE_CERT}, home=True)
        self.new = dict(self.old, code=15, version="2.0.9")

    def test_official_standard_and_guard_updates(self):
        for flavor in ("standard", "guard"):
            self.old["home"] = self.new["home"] = flavor == "standard"
            self.assertEqual(upgrade.verify(self.old, self.new, "2.0.9", flavor)["result"],
                             "upgrade_identity_verified")

    def test_rejects_signing_identity_downgrade_and_requirement_regressions(self):
        for field, value in (("package", "com.other"), ("code", 14), ("code", 13),
                             ("version", "2.0.8"), ("signers", set()), ("signers", {"other"}),
                             ("min_sdk", 29), ("target_sdk", 32), ("abis", {"arm64-v8a"}),
                             ("home", False)):
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                upgrade.verify(self.old, dict(self.new, **{field: value}), "2.0.9", "standard")

    def test_rejects_untrusted_baseline_and_wrong_flavor(self):
        with self.assertRaises(ValueError):
            upgrade.verify(dict(self.old, signers={"unknown"}), self.new, "2.0.9", "standard")
        with self.assertRaises(ValueError):
            upgrade.verify(self.old, self.new, "2.0.9", "guard")

    def test_existing_native_architectures_must_be_preserved(self):
        old = dict(self.old, abis={"armeabi-v7a", "arm64-v8a"})
        with self.assertRaises(ValueError):
            upgrade.verify(old, dict(self.new, abis={"arm64-v8a"}), "2.0.9", "standard")
        upgrade.verify(old, dict(self.new, abis=old["abis"] | {"x86_64"}), "2.0.9", "standard")


if __name__ == "__main__":
    unittest.main()
