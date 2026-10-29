# Guard and cleanup boundaries

The Android services collect events and execute actions. Decisions that do not need Android live in three small modules:

- `HomeTakeoverPolicy`: a real Home press (key, homekey broadcast, or Xiaomi's pause bounce) is never budgeted. Automatic checks — boot, wake, revival, usage stats, and stock-home windows during the first three minutes — need fresh HOME evidence, a short settle, and at most three takeovers in 30 seconds before a 60-second pause. That pause is what stops the 雷鸟 75S545C boot flip-flop (issue #14) from fighting the factory launcher for minutes. After the boot window, a new stock HOME window is answered without spending the budget. An unknown front, an app, or an HDMI input does not authorize a launch. Non-activity overlays do not cancel or confirm a takeover.
- `ServiceRecovery`: one outstanding rebind, cancellation when the user changes guard settings, and a fresh read of other services before delayed restoration.
- `CleanupPolicy`: package validation, foreground parsing across Android versions and explicit shell-result markers. Automatic runs never force-stop applications.

`HomeKeyFilterPolicy` remains the separate device-specific key-filter decision. The #15 exception is retained.

## Verification

Run both flavor unit-test suites, both release builds, both release lint tasks and `bash tools/test-install.sh`.

For a TV report, record model, firmware, API level, APK flavor and version. With the owner's agreement to each power operation, check cold boot and standby/wake separately. During the first minutes, open a video app and an HDMI source; neither should be taken over. Check physical Home and held volume keys, intentional visits to the stock launcher, and turning the guard off while service recovery is pending.

Collect `adb logcat -d -v time -s TigrisGuard ActivityTaskManager ActivityManager` promptly after reproducing. Do not equate a registered service with a bound service or a successful HOME command with visible takeover.

No Android-version floor was lowered. No new package-disable lists or DNS settings are applied by this release. Those require separate model-specific validation.
