"""Regression: AI routing is read-only and never changes existing brand behavior."""
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location(
    "tigris_replacement_routing", Path(__file__).with_name("tv-replacement.py")
)
replacement = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(replacement)


def snapshot(profile="xiaomi", classification="patchwall_selected",
             installed=True, candidate=True):
    return {
        "profile": profile,
        "device": {"manufacturer": profile, "brand": profile,
                   "model": "example-model", "firmware": "example-build",
                   "fingerprint": "example/fingerprint"},
        "tigris_installed": installed,
        "home_route": {"classification": classification,
                       "tigris_home_candidate": candidate},
        "home": {"physical_home": "unverified", "boot": "unverified",
                 "standby_wake": "unverified"},
    }


class AdaptiveInstallationTests(unittest.TestCase):
    def plan(self, **kwargs):
        return replacement.adaptive_install_decision(snapshot(**kwargs))

    def test_xiaomi_stock_home_routes_to_opt_in_probe(self):
        plan = self.plan()
        self.assertEqual(plan["next_step"], "offer_opt_in_reversible_native_home_probe")
        self.assertEqual(plan["automatic_system_package_changes"], [])
        self.assertEqual(plan["automatic_ads_package_changes"], [])

    def test_xiaomi_native_candidate_is_not_certified(self):
        plan = self.plan(classification="tigris_resolves_native_candidate")
        self.assertEqual(plan["next_step"], "verify_physical_home_boot_wake_and_memory_cleanup")
        self.assertEqual(plan["physical_home"], "unverified")
        self.assertEqual(plan["boot"], "unverified")

    def test_xiaomi_missing_home_candidate_never_disables_patchwall(self):
        plan = self.plan(classification="tigris_home_not_visible", candidate=False)
        self.assertEqual(plan["next_step"], "diagnose_missing_home_candidate_without_system_changes")
        self.assertFalse(plan["automatic_system_package_changes"])

    def test_xiaomi_fallback_home_is_unknown_not_license_to_disable(self):
        plan = self.plan(classification="fallbackhome_selected")
        self.assertEqual(plan["next_step"], "investigate_firmware_home_routing_read_only")
        self.assertFalse(plan["automatic_system_package_changes"])

    def test_xiaomi_not_installed_uses_standard_first(self):
        plan = self.plan(installed=False)
        self.assertEqual(plan["next_step"], "install_signed_standard_apk_then_inspect")

    def test_sony_existing_path_is_untouched(self):
        plan = self.plan(profile="sony", classification="different_vendor")
        self.assertEqual(plan["next_step"], "use_existing_standard_install_and_verify")
        self.assertTrue(plan["existing_sony_tcl_install_guard_paths_unchanged"])

    def test_tcl_existing_path_is_untouched(self):
        plan = self.plan(profile="tcl", classification="different_vendor")
        self.assertEqual(plan["next_step"], "use_existing_standard_install_and_verify")
        self.assertTrue(plan["existing_sony_tcl_install_guard_paths_unchanged"])

    def test_brand_or_firmware_never_authorizes_automatic_ads(self):
        for profile in ("xiaomi", "sony", "tcl", "coocaa", "hisense", "android"):
            with self.subTest(profile=profile):
                plan = self.plan(profile=profile)
                self.assertEqual(plan["automatic_ads_package_changes"], [])
                self.assertEqual(plan["system_ads"], "unverified")
                self.assertEqual(plan["performance_gain"], "unverified")

    def test_unknown_vendor_matches_safe_registry_path(self):
        self.assertEqual(self.plan(profile="android")["next_step"], "safe_apk_only_then_inspect")


if __name__ == "__main__":
    unittest.main()
