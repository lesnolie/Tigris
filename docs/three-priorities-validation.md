# Tigris development integration: v2.0.11 baseline + three priorities

**Status:** development branch only. Main is the existing v2.0.11 release. Do not release, deploy, merge a PR or modify main without user approval.

## Baseline preservation

This developer merge tree starts from the *entire* v2.0.11 main file tree and reapplies only the required improvements. It keeps:
- original signed-release versionCode 17/versionName 2.0.11 (do not offer debug/test APK as an upgrade);
- `MediaFiles.kt`, phone-side MP4 upload and local video wallpaper selection;
- Wallhaven, optional wallpapers/intro media, and the current updater progress/retry UI;
- the website's host OS / TV system selectors and the current installation mechanism;
- Guard and Standard flavor behavior and the same package identity.

Version increments and public APK naming are release-maintainer decisions, *not* part of this integration.

## 1. Easier installation

- Ten vendor-family policies are registered, including safe read-only routes for unverified firmware.
- Host install error messages distinguish TCL verifier refusal, signing conflicts, Android/ABI limits and authorization restrictions; do not uninstall existing user data.
- TV Settings HOME inspection offers brand-aware next-step text and a link to the installation guide, not a claim of real HOME takeover.
- README Chinese and English, website and flavor name distinguish:
  - **Standard**: declares HOME; first choice on Sony/stock Android TV and other firmware allowing third-party launchers.
  - **Compatibility Guard**: declares NO HOME; use only where the firmware blocks Standard, especially affected TCL/FFalcon builds, and separately authorize Guard.
  - Xiaomi/Redmi MIUI/HyperOS: start with Standard and test physical HOME; PatchWall may ignore it; Guard is conditional and firmware-specific.
  - Other Android brands: safe Standard install plus diagnostics; no blanket certification.
  - LG webOS: IPK preview only; Samsung Tizen and native VIDAA: Android APK unsupported.
- The **published APK filenames remain** `Tigris-v2.0.11.apk` and `Tigris-guard-v2.0.11.apk`. The Android application label of the development Guard build is `Tigris · 兼容守护版` / `Tigris · Guard Mode`.
- The website displays the **official v2.0.11 installer** separately from the *unreleased* dev-only extended vendor research. Unknown mechanisms may not be auto-solved.

## 2. Video wallpaper performance

- Replaced synchronous `TextureView.getBitmap()` sampling with bounded `PixelCopy.request` on a dedicated callback thread.
- Keeps local video upload (uses `MediaFiles.wallpaper(ctx)`), Apple aerial streams, and the unchanged main HomeActivity asynchronous glass bake integration.
- Limits copy dimensions and retry frequency, guards in-flight work and stale callbacks, and releases temporary Surfaces on stop/timeout.
- Must confirm on Android 9 Sony / low-end devices that PixelCopy succeeds with the actual SurfaceTexture producer. If unsupported it should degrade to static/cached glass without blocking UI.
- Main Glass / ControlCenter / UpdateActivity improvements remain untouched.

## 3. Dock 5 / 6 icons

- Optional 6 columns; default 5 remains unchanged.
- Dock and app grid use the same column math, bottom anchoring, reorder/folder persistence and dimension-keyed tile shadow cache.
- Returning from Settings recreates only when the chosen count changes.

## Validation and stop conditions

This is **source integration**, not an Android hardware certification. In the current environment, full Gradle Android SDK and connected TV hardware cannot be run; do not report such tests as passing unless CI says so.

Check both flavors and website after branch update:

```bash
python3 -m unittest discover -s tools -p 'test_*.py' -v
bash tools/test-install.sh
bash ./gradlew testStandardDebugUnitTest testGuardDebugUnitTest assembleStandardDebug assembleGuardDebug
bash ./gradlew lintStandardRelease lintGuardRelease
cd website && npm ci && npm run build
```

On actual TV validate: Standard/Guard manifest flavors, install/upgrade without uninstall, physical Home, reboot, standby and cleanup; 5/6 Dock focus/reorder/folders; Aerial and uploaded local video glass; rapid D-pad and Control Center; offline/failed PixelCopy fallback. Explicitly record **untested** when no physical result is available.

Do not publish APK or merge into main until tests and user authorization.
