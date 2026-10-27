# Tigris focused improvements: installation, video glass, and 5/6 Dock

Development branch only; no main merge, release, or production deployment.

## Product scope

Only two ongoing priorities: **tvOS-like appearance and interaction** and **easier installation/HOME takeover**. Vendor installation is separate from runtime vendor capabilities and brand-neutral features.

## 1. Firmware-aware installation (code completed; hardware coverage pending)

- `tools/installation_strategies.json` selects Sony, TCL/FFalcon, Xiaomi/Redmi, Skyworth/Coocaa, Hisense/Vidda, OPPO, XGIMI, Konka, LeTV or safe generic Android policy.
- `tools/vendor_install_guidance.json` contains upstream case studies; only read-only information, no firmware changes authorized by that material.
- `install.sh` classifies PackageManager install failures (TCL verifier, wrong certificate, SDK/ABI, system restrictions) and gives different next steps without uninstalling or modifying OEM components.
- `HomeInstallGuide.kt` shows next steps from the TV's Home check screen, and links to the official installation instructions.
- Website one-line install guide exposes the per-platform policy and has a **single** APK-only limitations notice next to download links. Standard/Guard and hardware confirmation are not conflated.
- Existing Sony native HOME and TCL Guard/T-Cast executors remain unchanged.
- Open work: no authorized ADB on a TV means this host cannot claim automatic Home takeover. Confirm Tcl T-Cast, physical HOME, reboot, wake, and restore with the affected user's real firmware.

## 2. Aerial video wallpaper glass ANR fix (code completed; full Android build/TV test pending)

On this development branch, the new `VideoWallpaper.kt` integrates Apple Aerial wallpaper playback into Home. It **never calls TextureView.getBitmap**. Instead:

- Android `PixelCopy.request(Surface, Bitmap, callback, Handler)` enqueues a GPU snapshot with callback on a dedicated `HandlerThread`; UI input is never synchronously held for a video readback.
- Only a 320px or 480px frame is captured at most every 1000ms or 500ms according to runtime device class.
- An in-flight guard prevents parallel CPU glass bakes. Generation tokens discard late results after an app pause/background change.
- Copy failure retries slowly, after repeated failures backs off to 10 seconds; a missing callback times out after 3 seconds and releases the temporary Surface.
- On failure, the video can keep playing with cached/static glass. Wallpaper source changes cancel previous video and return to standard photo/gradient rendering.

**Test on real TVs**: Sony Android 9 with Aerial video for at least 10 minutes; 5/6 Dock; rapid repeated D-pad/Home; control center; sleep/wake; videos unavailable; repeat after switching back to a static wallpaper. Verify no input-dispatch ANR (especially the Issue #53 trace), measure frame latency and repeat on a higher-performance TV. Source `VideoWallpaper.kt` was selectively added to this older development branch; this is **not a merge of newer main**. Feature parity with newer main (e.g. mobile upload of custom video) must be reconciled separately before a release.

## 3. Five/six Dock (code completed; TV focus/visual test pending)

- Existing users retain 5 columns; six is an opt-in Home Setting and is reversible.
- Home grid and Dock share one count, maintaining first-row icon-label hiding and D-pad column math.
- Changing the setting recreates the Activity after returning Home, retaining the existing app order and folder preferences.
- Dock bottom alignment and tile widths scale with the selected count; shadow bitmaps are cached per tile size.
- Pure Kotlin smoke checked: 1920px/112px side/56px gap gives tile widths 294px (5) and 236px (6); return to 5 and async frame-generation behavior works.

**Test on real TVs**: five to six and back without losing folder/order state, 1080p and 4K display metrics, both standard and Guard flavors, D-pad from Dock into second row and top-right controls, last incomplete row, app drag/reorder, focus zoom/sheens, Top Shelf, Control Center, video/static wallpaper backgrounds.

## Verification boundary

The new files and reference checks are present in the development branch. Pure Kotlin geometry and frame-state smoke checks passed in a standalone Kotlin runtime.

**Full Android Gradle compilation, Android lint, website build, automation against a TV, and hardware regression are not yet confirmed**. Do not call the three changes production ready until these pass. Test commands from project checkout:

```sh
python3 -m unittest discover -s tools -p 'test_*.py' -v
bash tools/test-install.sh
bash ./gradlew :app:testStandardDebugUnitTest :app:testGuardDebugUnitTest
bash ./gradlew :app:assembleStandardRelease :app:assembleGuardRelease
bash ./gradlew :app:lintStandardRelease :app:lintGuardRelease
cd website && npm ci && npm run build
```

No device packages or firmware were modified by this task. No PR was merged and no formal build was published.
