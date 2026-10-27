# Tigris

<p align="center">
  <a href="https://tigris-tv.pages.dev/">
    <img src="website/public/img/favicon.svg" alt="Tigris website icon" width="112" />
  </a>
</p>

**Languages:** [English](README.en.md) · [Simplified Chinese](README.md)

A tvOS-inspired launcher for Android TV, featuring a Liquid Glass Dock, Top Shelf-like content, a translucent Control Center, photographic wallpapers, and a photo-frame screensaver.

> The third project in the open-source Chinese Zodiac series: Tiger. *Tigris* means “tiger” in Latin and Greek.

Earlier versions were tested on Sony BRAVIA devices. New firmware-specific takeover fixes and video optimizations still need model-level verification. Other devices and firmware variants may require adjustments.

**Website:** [tigris-tv.pages.dev](https://tigris-tv.pages.dev) · **[v2.0.14 Release](https://github.com/lesnolie/Tigris/releases/tag/v2.0.14)** ([Standard APK](https://github.com/lesnolie/Tigris/releases/download/v2.0.14/Tigris-v2.0.14.apk) · [Compatibility Guard APK](https://github.com/lesnolie/Tigris/releases/download/v2.0.14/Tigris-guard-v2.0.14.apk) · [SHA-256 checksums](https://github.com/lesnolie/Tigris/releases/download/v2.0.14/SHA256SUMS-v2.0.14.txt))

[![Tigris promotional film](docs/screenshots/film.jpg)](https://tigris-tv.pages.dev/#film)

▶ [Watch the 42-second film](https://tigris-tv.pages.dev/#film) (with audio)

## Which APK should I install? Check the TV OS and firmware

> **Latest official release: v2.0.14.** The APK filenames remain `Tigris-v2.0.14.apk` (Standard) and `Tigris-guard-v2.0.14.apk` (Compatibility Guard). Guard describes the launcher mode: **not every TCL needs it, and it is not a universal Xiaomi workaround**. This release unifies Liquid Glass Settings and Apps & Updates, fixes scrolling texture jumps, and adds a handwritten first-use welcome on a blue sky, displayed and checked on a Sony TV.

| TV / platform | First choice | When to use another build | Important limits |
|---|---|---|---|
| Sony BRAVIA Android TV | **Standard** | Evaluate Guard only after firmware-specific HOME detection shows a real blocker | Some firmware uses a stock HOME alias or special registration |
| Stock Android TV / Google TV, general Android boxes | **Standard** | Only assess Guard after confirming HOME selection is blocked | APK installed does not verify physical Home, boot or wake |
| TCL / FFalcon Android TV | **Inspect first**. Use Standard when accepted; use **Compatibility Guard** on firmware that rejects launcher APKs | If `INSTALL_FAILED_VERIFICATION_FAILURE` occurs, use an official Guard package and an authorized on-TV T-Cast installation where supported | Guard declares **no HOME role**, and may need manufacturer-specific autostart and accessibility permissions |
| Xiaomi / Redmi MIUI TV / HyperOS TV | **Try Standard first** | When PatchWall ignores HOME, diagnose the exact firmware; Guard is only a conditional workaround | Do not disable PatchWall, Settings or other essential system components |
| Skyworth / Coocaa, Hisense / Vidda, OPPO, XGIMI, Konka, LeTV **running Android 9+** | **Standard as the safe starting point**, then inspect device firmware | Guard only for verified firmware limitations | No blanket brand-level takeover certification |
| LG **webOS** | **No Android APK**. Separate [webOS IPK developer preview](https://github.com/lesnolie/Tigris/releases/tag/webos-v0.1.0-preview.1) | Neither Standard nor Guard | Developer Mode; hardware unverified and no HOME takeover |
| Samsung **Tizen**, Hisense **native VIDAA** | **No supported Android APK** | Neither | Do not run Android ADB installer commands |

**Verification:** After installing, open another app and press the physical Home key; check reboot, standby/wake and long-press Home cleanup separately. If Home does not return to Tigris, keep the stock launcher enabled and use the [brand install and recovery guide](docs/installation-compatibility.md). Preserve the existing APK signing identity and user data; **do not uninstall merely to bypass installation restrictions**.

## v2.0.14: Glass Settings and sky welcome

Settings and Apps & Updates share refracting glass and the current Home wallpaper. Scrolling no longer replaces stale row textures after motion stops. Settings adds visual switches, app icons and checkboxes, glass dialogs and working About details. The first-use Tigris logo writes itself across a blue sky; press OK to continue, or replay it later from Settings. See the [v2.0.14 release notes](docs/releases/v2.0.14.md).

## v2.0.13: Optional casting and HGTV (install on demand)

From Tigris on the TV, open **Home Settings → Apps, casting & updates** to opt in to two independent applications:

- **MiracastReceiver casting** — downloads an official [GitHub Release](https://github.com/weekdayjast/MiracastReceiver/releases) only after the user requests installation.
- **HGTV short dramas** — downloads an official [HGTV Release](https://github.com/pan8664716/HGTV/releases) only when requested. HGTV is an independent third-party client relying on unofficial content interfaces; upstream changes can affect playback.

The redesigned **Apps & Updates** screen presents one tvOS-style card per app with just one contextual action (Get, Open or Update); download progress and Retry only appear when needed. Neither APK is bundled with Tigris, auto-installed for new users, or pinned to the Dock by default. Once installed, each becomes a normal launcher app and may be moved to the Dock. The installer checks release SHA-256 and a documented signing identity, and refuses incompatible upgrades instead of deleting app data. Automatic updates apply only to already installed apps. [Policy and limitations](docs/updater.md).

## Screenshots

![Home screen](docs/screenshots/home.jpg)

| Top Shelf content | All apps |
|---|---|
| ![Top Shelf](docs/screenshots/shelf.jpg) | ![All apps](docs/screenshots/grid.jpg) |
| **Control Center** | **Photo frame / screensaver** |
| ![Control Center](docs/screenshots/cc.jpg) | ![Photo frame](docs/screenshots/frame.jpg) |

## TV installation and compatibility reports

> **Snapshot: October 24, 2026. Official APKs require Android 9 (API 28) or newer.** The table records observations on **specific models, OS builds, and Tigris versions**. It is not blanket certification of a brand. **APK installed ≠ launcher activated ≠ HDMI, brightness, wallpaper and other features verified.** Historic bugs may be fixed in source without a subsequent hardware retest.

| TV model | Android / firmware | Installation / launch result | Evidence and limitations | Source |
|---|---|---|---|---|
| **Sony BRAVIA XR-55A80J** | Android TV 12 | ✅ Maintainer-tested on real hardware | Primary supported test TV; not a promise for every Sony model | Maintainer's project notes |
| **Sony BRAVIA VH1** | Android TV 10 / API 29 | ✅ Running on v1.9; rapid Back presses no longer reproduce the crash | Rapid Back had crashed v1.8; this does not certify every v2.0.2 feature | [#2](https://github.com/lesnolie/Tigris/issues/2) |
| **Sony KD-55X9500H** | Android TV 10 / API 29 | ✅ Reported running v1.9 | Glass capsule clipping was observed; fix landed on `main` via [#11](https://github.com/lesnolie/Tigris/pull/11), but no new-TV retest confirmed | [#10](https://github.com/lesnolie/Tigris/pull/10) |
| **FFalcon 75R685C** (`tcl_mt5879_cn`) | Android 11 / API 30, China firmware | ✅ Community hardware-tested with Guard build and TCL installation method | Direct `adb install` rejected; TCL T-Cast sideload used. Tested volume, vendor settings and source picker. Brightness slider intentionally hidden | [#1](https://github.com/lesnolie/Tigris/pull/1) |
| **Xiaomi MiTV-ANSP0** | Android 9 / API 28 | ⚠️ APK installed but **older release crashed on startup** | `BlendMode` API crash fixed in v2.0+; **no confirmed v2.0.2 retest on this TV** | [#9](https://github.com/lesnolie/Tigris/issues/9) |
| **Xiaomi MiTV-ASTR0** | Android 14 / Xiaomi OS3 | ⚠️ App usage reported, with functional problems | Wallpaper count stayed at zero; system rejected some guard service starts. Installation method and full feature set not verified | [#8](https://github.com/lesnolie/Tigris/issues/8) |
| **Older Xiaomi TV (model not supplied)** | OS/API unknown, below APK requirement | ❌ Official APK rejected (`INSTALL_FAILED_OLDER_SDK`) | Specific model and API level still requested. This says nothing about all Xiaomi TVs | [#3](https://github.com/lesnolie/Tigris/issues/3) |
| **Skyworth / Coocaa 7S710_A3** | Android 8 / API 26 | ❌ Official APK unsupported; ✅ contributor's API 26 fork worked | Fork tested HDMI2 and Back-to-Tigris. **Official v2.0.2 Coocaa adapter has not been hardware-verified** | [#4](https://github.com/lesnolie/Tigris/pull/4) |

**Which APK?** Use [Releases](https://github.com/lesnolie/Tigris/releases/latest): `Tigris-vX.Y.Z.apk` for most TVs; `Tigris-guard-vX.Y.Z.apk` plus [T-Cast installation](tools/tcast-push.py) for TCL/FFalcon China firmware. Each published file includes the release version; [the download helper](tools/download-release.py) selects the appropriate asset and checks its SHA-256. `INSTALL_FAILED_OLDER_SDK` means the device's API is below the APK's `minSdk`; trying another installer does not resolve that. Some OEM firmware requires additional home-guard setup; see [AGENTS.md](AGENTS.md).

### Submit a TV report

Please [open a structured TV compatibility report](https://github.com/lesnolie/Tigris/issues/new/choose) containing the exact model, Android version, Tigris version, APK, observed installation result and logs. **Automation reads the form's structured fields and proposes reviewed pull requests updating both README languages.** It never certifies self-reported devices, auto-merges, or writes unreviewed results to `main`. The historical reports above were manually corroborated from issues and PR discussions.

#### Incoming structured community reports (unverified)

<!-- TV_COMPATIBILITY_AUTO_START -->
| TV model | Android | Tigris / APK | Installation result | Issue state | Source |
|---|---|---|---|---|---|
| Sony BRAVIA 4K GB（BRAVIA_ATV2_CN，国行，2017 平台） | Android 9 (API 28) | v2.0.11 / Standard | Installed and launched (reported) | Open | [#53](https://github.com/lesnolie/Tigris/issues/53) |
| GOOJODOQ HY300 Pro+ | Android 14 | v2.0.9 / Standard | Installed and launched (reported) | Open | [#47](https://github.com/lesnolie/Tigris/issues/47) |
| 创维酷开 85P3DMAX | Android 11 | 2.0.2 / Custom fork | Installed and launched (reported) | Open | [#15](https://github.com/lesnolie/Tigris/issues/15) |
| 雷鸟 75S545C | Android 9 | V2.0.2 / Guard | Installed and launched (reported) | Open | [#14](https://github.com/lesnolie/Tigris/issues/14) |
<!-- TV_COMPATIBILITY_AUTO_END -->

## Install with an AI coding agent

You can also **install manually** using the APKs above. An agent is optional; it can help with ADB setup, default-home selection and vendor-specific features.

### 1. Enable ADB debugging on the TV

Connect the TV and computer to the same LAN and find the TV's IP address (usually Settings → Network / About).

| TV | Enabling ADB |
|---|---|
| **Xiaomi / Redmi** | Settings → About → press OK on **Model** repeatedly to enable developer mode; under Account & Security, enable **ADB debugging** and **Unknown sources** |
| **TCL / FFalcon** | Settings → About → Device information, then press **Up, Down, Left, Right** in quick succession; the hidden **More** section contains ADB |
| **Sony BRAVIA** | Settings → System → About → press OK seven times on **Build**; then Settings → Developer options → USB debugging |
| **Stock Android TV / Google TV / other OEMs** | Settings → Device Preferences → About → press Build seven times; enable USB, network, or ADB debugging in Developer options |

When the TV prompts to allow debugging from your computer, select **Allow**, preferably **Always allow**. Xiaomi may disable ADB on waking; Tigris has an optional setting to keep it enabled.

### 2. Ask an agent to install it

Give a command-capable agent such as Codex or Claude Code:

> Follow https://github.com/lesnolie/Tigris/AGENTS.md to install Tigris on my TV. My TV's IP address is 192.168.x.x. Ask before changing TV system settings.

The [installation guide for agents](AGENTS.md) covers connecting, choosing the APK, configuring the launcher, requesting permissions and Sony-specific setup. Approve any on-TV prompts yourself. On TCL, accept the **T-Cast / 多屏互动** installation prompt.

### 3. Install or restore with Bash (macOS / Linux)

Install Android Platform-Tools (`adb`), `curl` and Python 3 on your computer first. The script downloads the latest stable APK, checks its SHA-256, backs up the original HOME configuration and preserves app settings on compatible upgrades. Replace the sample IP with your TV's address.

```sh
# Standard APK (most Android TVs):
curl -fsSL https://raw.githubusercontent.com/lesnolie/Tigris/main/install.sh \
  | TV=192.168.1.20 bash

# Guard APK for TCL / FFalcon China firmware:
curl -fsSL https://raw.githubusercontent.com/lesnolie/Tigris/main/install.sh \
  | TV=192.168.1.20 bash -s -- -s guard

# Restore using the same computer and saved snapshot:
curl -fsSL https://raw.githubusercontent.com/lesnolie/Tigris/main/install.sh \
  | TV=192.168.1.20 bash -s -- restore
```

To explicitly keep ADB enabled, add `KEEP_ADB=1` before `bash`. New releases ship `Tigris-vX.Y.Z.apk`, `Tigris-guard-vX.Y.Z.apk` and `SHA256SUMS-vX.Y.Z.txt`; the installer also accepts older filenames. The installer will not silently uninstall an APK when signatures differ. See the [installation and restore guide](docs/installation.md). On Windows, follow [AGENTS.md](AGENTS.md) with a command-capable desktop agent or use PowerShell/ADB manually; phone chat alone cannot operate the TV.

## Features

### What's new in v2.0.6

- **Choose your app order:** Settings → App → **Recently opened apps first**. Off by default on a fresh v2.0.6 install or first upgrade from an older release. Keep your current layout and folders in manual mode, or turn it on to move a successfully launched app or folder to the front. Toggling does not reset the layout.
- **Versioned release assets:** `Tigris-v2.0.6.apk`, `Tigris-guard-v2.0.6.apk`, and `SHA256SUMS-v2.0.6.txt`. The in-app updater and install script recognize both current and historical release naming.
- **Upgrade in place:** compatible installations retain their data and settings using the established stable signing key. See the [v2.0.6 release notes](docs/releases/v2.0.6.md).

### Highlights introduced in 2.0.4

App folders, phone-based avatar upload, an update center for Tigris and MiracastReceiver, and frosted app-list scrolling. The Dock and top-right controls scroll off-screen with the grid. Bundled startup media and settings artwork are defaults; existing customizations take priority. Upload or reset an avatar from the TV's Settings screen using the on-screen QR code. Updates are under Control Center → Settings → Software updates & casting.

### Core features

- **tvOS-style home screen:** docked 16:9 app tiles, focus zoom, directional tilt, animated light sweep and shadow; optional portrait icon; compact clock, large clock with date, or no clock; 12/24-hour time.
- **Settings:** a large-icon tvOS-inspired settings screen with grouped controls. Focus animation and Control Center style default to *Auto*, choosing a lighter style on TVs with under 2.5 GB RAM, Android below 10, or fewer than four CPU cores.
- **Recently opened apps first (optional):** off by default when installing v2.0.6 or first upgrading from an older release; after that, your saved preference is retained. Turn it on under Settings → App → Recently opened apps first to move successfully launched apps or folders to the front; turn it off to preserve the current manual order. Long-press a tile and choose Edit Home Screen to rearrange it. Focus changes, failed launches and HDMI switching never reorder apps.
- **Manage apps:** long-press items for actions including removing preinstalled user-visible packages with `pm uninstall --user 0` when allowed (system partition APKs are not deleted). Restore them from the deleted-preinstalled-apps section. Essential TV system components are protected from this action.
- **Liquid Glass Dock:** precomputed blur, refraction, highlights and shadows from the wallpaper avoid expensive per-frame GPU effects.
- **Top Shelf-style content:** selected app tiles can show public-feed content from Bilibili and Douyu. Their corresponding TV apps are required.
- **Control Center:** open with Back or the top-right icon. Floating glass controls or a lower-overhead dark panel; network, sleep, shutdown, restart, memory cleanup, photo frame, settings, brightness, volume and TV inputs.
- **Boot/wake animation:** optional video uploaded from your phone, or a built-in Tigris wordmark animation. Choose startup only or startup and wake, with adjustable wake delay; skip with any key.
- **Wallpapers:** Bing daily imagery, 360 scenery and pet images, all-ages anime (Wallhaven, falling back to China-accessible sources), artworks, personal photos, custom image sources and gradient themes. Download photos as needed and clean old cached files.
- **Wallpaper picker and phone setup:** select previews on TV, scan a QR code to upload photos, provide custom photo/image-feed URLs, change wallpapers/screensavers, upload a boot movie or customize the settings icon and backdrop. The LAN configuration page runs while the picker is open and for five minutes afterward. **The phone companion page now automatically follows the phone browser's language (Chinese or English)**, independently of the TV's system language, including upload buttons, wallpaper labels, status messages and errors.
- **Photo frame and Apple TV aerial screensaver:** slowly pan/zoom photos or stream aerial footage from Apple with location labels. Can become the system screensaver; video playback is not intentionally interrupted.
- **HDMI shortcuts on the home screen:** Control Center → Inputs → Show on Home, where available.
- **Home guard:** helps keep Tigris in front on OEM firmware that forces its own launcher. Does not require root and is off by default.
- **Background memory cleanup:** periodic cleanup and a configurable keep-alive list.

## Compatibility by feature

These are **implementation capabilities**, not proof of hardware compatibility on every brand.

| Feature | Standard Android TV 9+ | Local ADB approval | Sony-specific |
|---|:---:|:---:|:---:|
| Home screen, Dock, focus effects | ✓ | | |
| Bilibili / Douyu Top Shelf | With corresponding TV apps | | |
| Wallpapers, photo frame and basic Control Center | ✓ | | |
| Cache cleanup | ✓ | | |
| Force-stop selected background apps | | ✓ | |
| Remove certain preinstalled apps for current user | | ✓ | |
| Sleep, shutdown and restart | | ✓ | |
| Set photo frame as screensaver / idle schedule | | ✓ | |
| Volume | Android media stream | | BRAVIA volume |
| Inputs / HDMI | When exposed by system TV framework | | Current input and switching |
| Backlight slider | Xiaomi adapter may use an approved ADB grant | | BRAVIA backlight |
| Home guard and recovery | Device-dependent | ✓ | |

Sony TVs can use the local BRAVIA IP-control endpoint for brightness, TV volume and input selection when enabled. Generic Android TVs use `AudioManager` for volume and `TvInputManager` for inputs. Android provides no universal backlight API; unsupported sliders are hidden. ARC/eARC setups may require vendor-specific volume handling.

### Home guard and permissions

On firmware that enforces the manufacturer's launcher, Tigris uses an accessibility service to watch foreground window changes without reading screen content. Automatic takeover requires a confirmed stock HOME window; app windows, HDMI inputs and dialogs cancel pending takeovers. Boot, wake and service recovery alone do not initiate a takeover, and repeated attempts are rate-limited. The notification listener helps recover the process after OEM termination without reading notifications.

| Permission or service | Purpose |
|---|---|
| `WRITE_SECURE_SETTINGS` | With user authorization, restore guard, screensaver and ADB settings; support Xiaomi backlight control. |
| Notification listener | Help revive the process after firmware cleanup without reading notification content. |
| `SYSTEM_ALERT_WINDOW` | Permit background home launches where supported by firmware. |
| TCL `APP_AUTO_START` / `APP_ASSOC_START` | Allow autostart and associated launches on compatible TCL firmware. |

The home guard is off by default. Firmware behavior varies, so cold boot, standby/wake and Home key behavior must be verified on each TV. See [guard stability notes](docs/guard-stability.md).

### TCL / FFalcon China firmware

Community-tested on FFalcon 75R685C ([PR #1](https://github.com/lesnolie/Tigris/pull/1)). This firmware does not expose standard HDMI input services or a usable brightness API. Tigris offers a single **TCL source picker** action, opens TCL's settings app and hides the backlight slider. It is **not** direct control of each HDMI port.

For this specific firmware, `adb install` rejects the package and APKs declaring themselves HOME are blocked. Use the **Guard** APK via TCL's built-in **T-Cast (多屏互动)** service and [tools/tcast-push.py](tools/tcast-push.py), then enable the home guard as appropriate.

### Xiaomi / Redmi China firmware

The Xiaomi adapter can use `picture_backlight` for brightness after `WRITE_SECURE_SETTINGS` approval, Xiaomi's external-source player for input switching, and Xiaomi's settings/Wi-Fi screens. It also has a workaround for the manufacturer's screensaver. **These paths have not been comprehensively tested by this project's maintainers on Xiaomi hardware.** See the issue reports above for observed problems.

**Never disable Xiaomi's stock launcher** (`com.mitv.tvhome` / PatchWall); it can leave the TV with a black screen on boot. Use Tigris's optional home guard instead. The guard uses an accessibility service to detect vendor-home foreground changes, a notification listener to help restart after OEM process termination, and optional secure settings/overlay permissions. Those operations require user authorization and can behave differently across firmware versions.

### Skyworth / Coocaa

`tv/CoocaaAdapter.kt` uses the vendor's source picker when standard HDMI input discovery is empty. Official v2.0.2 support remains **unverified on Coocaa hardware**. The community's Android 8 / API 26 fork test on 7S710_A3 is not an official APK install test ([PR #4](https://github.com/lesnolie/Tigris/pull/4)). The official app still requires API 28+.

### Other Android TVs

The brand-specific adapters live under [`app/src/main/java/com/leslie/tvhome/tv/`](app/src/main/java/com/leslie/tvhome/tv/): `TvControls.kt`, `AndroidAdapter.kt`, `SonyAdapter.kt`, `TclAdapter.kt`, `XiaomiAdapter.kt`, and `CoocaaAdapter.kt`. Each brand overrides only its non-standard behavior; standard Android methods remain the fallback.

To contribute a new model, have a developer or coding agent follow [AGENTS.md §9](AGENTS.md#9-adapting-tigris-to-a-tv-that-isnt-fully-supported), inspect that TV's actual interfaces and submit a pull request. Never disable/uninstall a stock launcher or change critical TV settings without the owner's approval.

## Build and install manually

Requires **JDK 17**, Android SDK **compileSdk 36**, Gradle wrapper and ADB. Or simply download [the latest release](https://github.com/lesnolie/Tigris/releases/latest).

```sh
./gradlew assembleStandardRelease assembleGuardRelease
adb connect <TV_IP>:5555
adb install -r app/build/outputs/apk/standard/release/app-standard-release.apk
adb shell cmd package set-home-activity com.leslie.tvhome/.HomeActivity
```

These locally built APKs use the local debug key and may not be installable over an already installed release signed with another key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). **Back up configuration and request consent before uninstalling to change signing keys.** On TCL use the versioned Guard APK and its dedicated installation method.

For supported Sony BRAVIA TVs, register an IP-control client (the TV displays a four-digit PIN):

```sh
BODY='{"method":"actRegister","id":1,"version":"1.0","params":[{"clientid":"tvhome:1","nickname":"TVHome","level":"private"},[{"value":"yes","function":"WOL"}]]}'
curl -s http://<TV_IP>/sony/accessControl -d "$BODY"
curl -s -u ":<PIN>" http://<TV_IP>/sony/accessControl -d "$BODY"
```

See [AGENTS.md](AGENTS.md) for permissions, setup and verification. Official release APKs are signed with the project's stable release key; locally built APKs may use a different key, so verify signatures before attempting an in-place upgrade.

## Casting recommendation

Tigris has no built-in casting receiver. The open-source [Miracast Receiver](https://github.com/weekdayjast/MiracastReceiver) is one option for AirPlay, DLNA and Miracast (some firmware features may require root). Install the APK matching your TV's CPU architecture. Tigris's cleanup keep-alive defaults account for this app.

## License and third-party notices

Tigris is licensed under [MIT](LICENSE). Copyright and licensing details for third-party code, fonts, icons and dependencies are in [third-party notices](THIRD_PARTY_NOTICES.md). Community contributions are linked in the corresponding Issues and PRs, including FFalcon [#1](https://github.com/lesnolie/Tigris/pull/1), Coocaa [#4](https://github.com/lesnolie/Tigris/pull/4), and focus improvements [#10](https://github.com/lesnolie/Tigris/pull/10) / [#11](https://github.com/lesnolie/Tigris/pull/11).

This project is not affiliated with Apple, Sony, Xiaomi, TCL, Bilibili or Douyu. Apple TV aerial videos stream from Apple's servers; the app does not distribute those videos. Wallpaper and screenshot media belong to their respective rights holders.
