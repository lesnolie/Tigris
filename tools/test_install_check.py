"""Behavioral evidence tests; no TV or ADB needed."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest

spec = importlib.util.spec_from_file_location("checker", Path(__file__).with_name("check-install.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class HomeCheckTests(unittest.TestCase):
    def check(self, windows, start="Status: ok", home_exit=0):
        windows = iter(windows)
        def run(args, **kwargs):
            cmd = args[4:]
            text, code = "", 0
            if cmd[:2] == ["am", "start"]:
                text = start
            elif cmd == ["dumpsys", "window"]:
                text = next(windows)
            elif cmd[:2] == ["input", "keyevent"]:
                code = home_exit
            return SimpleNamespace(stdout=text, stderr="", returncode=code)
        return checker.Check("test:5555", run, lambda _: None).verify(checker.HOME)

    def window(self, name):
        return "mCurrentFocus=Window{aa u0 " + name + "}"

    def test_stable_home_from_settings_passes(self):
        self.assertEqual("passed", self.check([self.window("com.android.settings/.Settings")] +
            [self.window(checker.HOME)] * 8))

    def test_already_on_tigris_is_not_evidence(self):
        self.assertEqual("unverified", self.check([self.window(checker.HOME)]))

    def test_vendor_wins_despite_successful_home_command(self):
        self.assertEqual("failed", self.check([self.window("com.android.settings/.Settings")] +
            [self.window("com.vendor/.Home")] * 8))

    def test_transient_tigris_does_not_pass(self):
        self.assertEqual("failed", self.check([self.window("com.android.settings/.Settings")] +
            [self.window(checker.HOME)] * 5 + [self.window("com.vendor/.Home")] * 3))

    def test_missing_focus_is_unverified(self):
        self.assertEqual("unverified", self.check([self.window("com.android.settings/.Settings")] + [""] * 8))

    def test_settings_error_with_exit_zero(self):
        self.assertEqual("unverified", self.check([], start="Error: Activity not started"))

    def test_exact_component_not_package_substring(self):
        self.assertEqual("failed", self.check([self.window("com.android.settings/.Settings")] +
            [self.window("com.leslie.tvhome.fake/.HomeActivity")] * 8))

    def test_stale_focused_app_is_not_current_window(self):
        self.assertIsNone(checker.focus("mCurrentFocus=null\nmFocusedApp=com.leslie.tvhome/.HomeActivity"))

    def test_activity_dump_mfocusedapp_verifies_home(self):
        names = iter(["com.android.settings/.Settings"] + [checker.HOME] * 8)

        def run(args, **kwargs):
            cmd = args[4:]
            if cmd[:2] == ["am", "start"]:
                return SimpleNamespace(stdout="Status: ok", stderr="", returncode=0)
            if cmd == ["dumpsys", "activity", "activities"]:
                return SimpleNamespace(
                    stdout="mFocusedApp=ActivityRecord{u0 " + next(names) + " t1}",
                    stderr="", returncode=0)
            if cmd[:2] == ["input", "keyevent"]:
                return SimpleNamespace(stdout="", stderr="", returncode=0)
            return SimpleNamespace(stdout="mCurrentFocus=Window{aa u0 com.vendor/.Ignored}", stderr="", returncode=0)

        self.assertEqual("passed", checker.Check("test:5555", run, lambda _: None).verify(checker.HOME))

    def test_component_aliases_normalize(self):
        self.assertEqual(checker.component(checker.HOME), checker.component("com.leslie.tvhome/com.leslie.tvhome.HomeActivity"))


if __name__ == "__main__":
    unittest.main()
