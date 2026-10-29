# Installing Tigris — instructions for AI agents

## Module development boundaries / 模块开发边界

Read [the bilingual module guide](docs/modules.md) and the
[cross-vendor Aerial installation study](docs/aerial-cross-vendor-install-study.md)
before extending installation strategies. Third-party sample behavior is diagnostic
evidence only, not permission to change the user's firmware.
Host installation belongs in `tools/` and `install.sh`; TV capabilities belong in
`tv/TvControls.kt` and `TvAdapter`; shared features belong in the runtime, never in
brand installers. Preserve existing preference keys, visuals, Sony native HOME and
TCL/FFalcon Guard/T-Cast operations. No runtime package manager workarounds in adapters.
Do not merge, publish, deploy, disable stock HOME/settings, or mark hardware verified
without explicit task authorization and evidence. Hardware reports are not authorization.

安装器在电脑运行，设备能力在 tv/，通用功能不按品牌复制。保留设置键、视觉效果和既有成功路径。
不要将宿主安装命令放入运行时 Adapter。历史实机报告不代表本次授权。

Full source checkout (v2.0.9+) / 完整源码检出目录（v2.0.9 起）:
`python tools/install-plan.py --platform android --serial 192.168.1.20:5555`
(macOS/Linux: `python3`). This read-only plan is shared by AI, manual and automatic
installation; append `--execute` only when installation is requested. It uses the
reviewed `installation_strategies.json` registry. Windows execution requires Git Bash
on PATH. Non-Android platforms must use their separate installer, never ADB.
`install.sh` remains the backwards-compatible low-level executor. Never assume the
an older checkout includes this entry point; use v2.0.9 or newer.

AI、手动、一键安装复用上述计划；需要执行安装时加 `--execute`。Windows 执行需要 Git Bash。
先确认平台；非 Android 不调用 ADB。广告候选不是安全清单：只有精确固件审查记录匹配、
逐包明确授权且保存原状态后才可执行；当前没有已批准固件，因此广告处理只做诊断。

> **LG webOS 测试包 / Developer preview:** [webos-v0.1.0-preview.1](https://github.com/lesnolie/Tigris/releases/tag/webos-v0.1.0-preview.1) · [IPK 安装、启动、更新和卸载 / Setup guide](https://github.com/lesnolie/Tigris/blob/main/docs/webos-preview-install.md)。已提供打包 IPK，无需自行构建；未做实机测试，不是稳定版，不接管 Home。Prebuilt IPK available; hardware untested, no Home takeover.

> **统一安装验收 / Shared compatibility rules:** [按品牌排查、遥控器/U 盘安装、电脑检查与四项验收](docs/installation-compatibility.md). AI 与手动安装使用同一个 `tools/check-install.py`；先验证 Home，失败才处理品牌限制。APK 安装不等于桌面接管，未测项目必须标记未验证。


> **Platform check first:** These instructions and the current Tigris releases support **Android TV/Google TV** only. For **LG webOS**, do **not** use ADB or any Android `pm`, `am` or HOME commands. Read [docs/WEBOS_AI_INSTALL.md](docs/WEBOS_AI_INSTALL.md) for the future LG Developer Mode + SSH preflight, native Home Helper probe, safe installation gates and fallback. As of 2026-10-25, a separate webOS **source preview** and `webos/install.sh` can package/install a normal webOS application (subject to LG device authorization), but there is **no released stable IPK or tested Home Helper**. Never claim Home interception or TV hardware validation has been completed. If webOS is detected, explain this clearly and stop Android installation instructions. The preflight design allows a future AI installer to check compatibility before installing the full desktop, but a physical Home-key test is still required for final verification.

You are installing Tigris, an Android TV launcher, onto the user's TV from their computer. Follow these steps in order. Talk to the user in their language (most users write Chinese). The TV is someone's home device: whenever a step below says to ask, ask first.

## AI adaptive installation / AI 自适应安装

When the request is to replace a TV's factory launcher and reduce firmware ads,
**first run the existing read-only `full-plan` inspection and follow its `adaptive_install` decision**.
Use [the capability-first AI installation policy](docs/ai-adaptive-installation.md).
Do not pick a Xiaomi/Redmi workaround by brand, model name, MIUI/HyperOS label,
or copied GitHub commands alone. Historical TV reports are evidence for investigation,
not authorization to disable a system component. Continue to use the existing
Sony and TCL/FFalcon installer/Guard paths unchanged. Treat advertising and performance
as separately verified outcomes; do not call installation success full HOME takeover.
Only propose an opt-in HOME probe when the live device meets the safety preconditions.
Never automatically disable stock HOME, Settings, FallbackHome, HDMI, or OTA.

## Hard rules

- Never run `svc power shutdown`, `svc power reboot`, `reboot` or `input keyevent KEYCODE_POWER` / `KEYCODE_SLEEP` without asking. A powered-off TV can only be turned back on with its remote.
- Never `pm uninstall` a system app or the TV's own launcher.
- **Never disable or uninstall the stock launcher on any brand in the installation flow.** Keep it available for recovery; use verified default HOME or optional Home Guard.
- Before you send `KEYCODE_DPAD_CENTER` / `ENTER` on the TV, check what is focused (step 6). Many Tigris buttons power the TV off or put it to sleep.
- Do not change other TV settings (animation scales, app permissions, background restrictions) unless the user asks.

## 0. What you need

- A computer on the same local network as the TV, with `adb` installed (Android SDK Platform-Tools). Check with `adb version`. If it is missing, install it: Windows `winget install Google.PlatformTools`, macOS `brew install android-platform-tools`, Debian/Ubuntu `apt install adb`.
- The TV's IP address. Ask the user. It is under Settings → Network (网络) or About (关于) on most TVs.
- ADB debugging enabled on the TV. Most users have never done this, so if `adb connect` fails in step 1, walk them through it for their brand. Ask for the brand if you don't know it.

| TV | How to turn on ADB |
|---|---|
| **Xiaomi / Redmi** | 设置 → 关于 → **产品型号**: press OK repeatedly until it says developer mode is on. Then 设置 → 账号与安全: turn on **ADB 调试** and **安装未知来源的应用**. |
| **TCL / FFalcon (雷鸟)** | 设置 → 关于 → **本机信息**, then press **Up, Down, Left, Right** on the remote, less than a second apart. A **更多** group appears at the bottom; turn on **ADB** there. |
| **Sony BRAVIA** | Settings → System → About → press OK on **Build** (Android TV OS build) 7 times. Then Settings → System → Developer options → turn on **USB debugging**. |
| **Stock Android TV / Google TV**, Hisense and others | Settings → Device Preferences → About → press **Build** 7 times. Then turn on **USB debugging** in Developer options (sometimes called Network debugging or ADB debugging). |

The first time the computer connects, the TV asks "Allow USB debugging?" (允许 USB 调试吗？). The user must choose **Allow** (允许) and tick **Always allow** (始终允许).

## 1. Connect

```sh
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 get-state     # should print "device"
```

If it prints `unauthorized`, the TV is showing "Allow USB debugging?". Ask the user to tick "Always allow from this computer" and press Allow, then retry. If the connection is refused, ADB is off (step 0). Xiaomi turns it off again on every wake from standby until Tigris's "keep ADB on" option is set (step 4). Use `-s <TV_IP>:5555` on every later command.

## 2. Check the TV

```sh
adb -s <TV_IP>:5555 shell getprop ro.build.version.sdk          # must be >= 28 (Android 9)
adb -s <TV_IP>:5555 shell getprop ro.product.manufacturer
adb -s <TV_IP>:5555 shell getprop ro.product.model
adb -s <TV_IP>:5555 shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s <TV_IP>:5555 shell settings get secure screensaver_components
adb -s <TV_IP>:5555 shell settings get secure enabled_accessibility_services
adb -s <TV_IP>:5555 shell settings get secure enabled_notification_listeners
```

Note the current home activity (the stock launcher), the current screensaver and the accessibility services; you need them to undo the install. Tell the user which features they will get (see the compatibility table in README.md). Section 9 covers adapting the app when something doesn't work.

## 3. Install

There are two builds:

- `Tigris-vX.Y.Z.apk` (standard) declares itself a home screen. Try the standard build unless this firmware is known to reject HOME APKs.
- `Tigris-guard-vX.Y.Z.apk` declares no home screen and appears as an ordinary app. Some TCL/FFalcon firmware rejects APKs declaring HOME; do not generalize to all models. The guard build is otherwise identical; the home guard (section 10) keeps it in front.

**Always resolve the latest Release asset**, instead of constructing a `releases/latest/download/Tigris.apk` URL (new assets include a version). The helper verifies SHA-256 and prints the downloaded filename:

```sh
curl -fsSL -o download-release.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/download-release.py
APK=$(python3 download-release.py standard)
adb -s <TV_IP>:5555 install -r "$APK"
```

On Windows PowerShell use `curl.exe` to download the helper, `$apk = python .\download-release.py standard`, and `adb -s <TV_IP>:5555 install -r $apk`.

**If it fails with `INSTALL_FAILED_VERIFICATION_FAILURE`** (TCL: its verifier refuses every adb install), install the guard build through TCL's own 多屏互动 (T-Cast) instead. It needs Python 3 and the repository's `tools/tcast-push.py`:

```sh
GUARD_APK=$(python3 download-release.py guard)
curl -fsSL -o tcast-push.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/tcast-push.py
python3 tcast-push.py <TV_IP> "$GUARD_APK"
```

The script serves the APK from this computer on port 8765. The TV shows "多屏互动：正在发起应用推送" in a corner. Tell the user to choose **同意** on the TV. If the computer's firewall asks, allow the connection. If the script can't reach port 6553 (no 多屏互动 on that model), fall back to installing on the TV itself:

```sh
adb -s <TV_IP>:5555 push "$GUARD_APK" /sdcard/Download/Tigris-guard.apk
adb -s <TV_IP>:5555 shell am start -n com.dangbeimarket/.ui.apkmanager.ApkManagerActivity   # if 当贝市场 is installed
```

Then ask the user to pick Tigris in Dangbei's package manager (or with any file manager on the TV) and install it. Check the result with `adb shell pm path com.leslie.tvhome`.

If GitHub is unreachable, build from source instead: clone the repo, then `./gradlew assembleStandardRelease assembleGuardRelease` (needs JDK 17 and an Android SDK; set `sdk.dir` in `local.properties`). The APKs are `app/build/outputs/apk/standard/release/app-standard-release.apk` and `app/build/outputs/apk/guard/release/app-guard-release.apk`.

## 4. Make it the home screen

Standard build. Hold the HOME role first; some firmwares have no `cmd role` and that line fails harmlessly:

```sh
adb -s <TV_IP>:5555 shell cmd role add-role-holder --user 0 android.app.role.HOME com.leslie.tvhome
adb -s <TV_IP>:5555 shell cmd package set-home-activity com.leslie.tvhome/.HomeActivity
adb -s <TV_IP>:5555 shell input keyevent KEYCODE_HOME
```

`install.sh` enables the guard for Xiaomi/Redmi, TCL/雷鸟, Coocaa/Skyworth and Hisense even when this Home check passes. On Sony and Google TV it enables the guard when a stock launcher still has HOME priority above 0. Other Android TVs get the guard when the Home check fails. The guard never disables the stock launcher.

If Home still opens the original launcher later, re-grant and restart the guard without reinstalling:

```sh
TV=<TV_IP> bash install.sh repair
```

First run the shared checker from docs/installation-compatibility.md. Apply these grants whenever the guard is turned on (the repair command and `Guard.grantViaLocalAdb` do the same set):

```sh
P=com.leslie.tvhome
adb -s <TV_IP>:5555 shell pm grant $P android.permission.WRITE_SECURE_SETTINGS
adb -s <TV_IP>:5555 shell pm grant $P android.permission.PACKAGE_USAGE_STATS
adb -s <TV_IP>:5555 shell cmd notification allow_listener $P/$P.RevivalListener
adb -s <TV_IP>:5555 shell appops set $P SYSTEM_ALERT_WINDOW allow
adb -s <TV_IP>:5555 shell appops set $P WRITE_SETTINGS allow
adb -s <TV_IP>:5555 shell appops set $P GET_USAGE_STATS allow
adb -s <TV_IP>:5555 shell appops set $P android:get_usage_stats allow
adb -s <TV_IP>:5555 shell appops set $P ACCESS_RESTRICTED_SETTINGS allow
adb -s <TV_IP>:5555 shell appops set $P APP_AUTO_START allow      # TCL; other names and firmwares may error
adb -s <TV_IP>:5555 shell appops set $P APP_ASSOC_START allow
adb -s <TV_IP>:5555 shell appops set $P AUTO_START allow
adb -s <TV_IP>:5555 shell appops set $P ASSOC_START allow
```

| Grant | What it is for |
|---|---|
| `WRITE_SECURE_SETTINGS` | Switches the home guard back on after the system turns it off; keeps the photo frame as screensaver; keeps ADB on; Xiaomi's backlight slider |
| `PACKAGE_USAGE_STATS` / `GET_USAGE_STATS` | Sees a launcher that raises no accessibility window (older PatchWall, some VIDAA) |
| `ACCESS_RESTRICTED_SETTINGS` | Android 13+ otherwise blocks a sideloaded accessibility service. The TV may also need 允许受限设置 / Allow restricted settings on the app |
| `WRITE_SETTINGS` | System settings the guard and picture controls write |
| Notification listener | Helps recover after process cleanup; low-RAM Android 9 also uses the sync adapter. No notification is read |
| `SYSTEM_ALERT_WINDOW` | Opening the home screen from the background after boot |
| TCL `APP_AUTO_START` / `APP_ASSOC_START` and `AUTO_START` / `ASSOC_START` | TCL blocks third-party apps from starting at boot otherwise. Unknown names error on other brands; ignore that |

If step 5 shows the stock launcher coming back, turn on the home guard (section 10). On Xiaomi, also ask whether to keep ADB on: Xiaomi switches ADB off on every wake, which makes later updates through adb impossible until the user turns it on again in settings.

```sh
adb -s <TV_IP>:5555 shell am broadcast -a com.leslie.tvhome.CONFIG -n com.leslie.tvhome/.ConfigReceiver --ez guard true --ez keep_adb true
# prints data="guard=true keep_adb=true secure_settings=true"
```

The user can change both later in 设置 → 桌面守护 / 保持 ADB 调试开启. The installation check and 桌面守护 rows are available on every Android brand; 保持 ADB 调试开启 on Xiaomi only.

## 5. Verify

Take a screenshot and look at it:

```sh
adb -s <TV_IP>:5555 shell screencap -p /sdcard/tigris-check.png
adb -s <TV_IP>:5555 pull /sdcard/tigris-check.png tv.png
```

You should see a compact clock, two round controls and an optional profile portrait top-right, and a row of app tiles on a glass dock near the bottom. Back opens the Control Center: separate floating glass controls, or a dark panel on low-end TVs. Press Home (`input keyevent KEYCODE_HOME`) and check again.

If the stock launcher appears instead:

- On Xiaomi, TCL and other Chinese firmwares, turn on the home guard (step 4, section 10). **Do not disable the stock launcher on Xiaomi.**
- On all other Android TVs, verify actual Home behavior and use the guard only if needed. Keep the stock launcher enabled. If verification still fails, report it without claiming incompatibility is permanent.

On first start the app downloads background photos (needs internet). The first minute may show a gradient.

## 6. Local debugging authorization (power, sleep, deep memory cleanup)

On first start Tigris asks to enable these features. When the user presses "开始", the TV shows "Allow debugging?" again, this time from the TV to itself. The user must tick "Always allow" and press Allow. Check the screen with `screencap` and guide them. You can verify afterwards: the Control Center (Back key on the home screen) no longer shows a "本机调试未授权" line. Once this is allowed, Tigris can also give itself the step 4 grants (设置 → 桌面守护 does that).

If you need to press buttons for the user, first find the focused element:

```sh
adb -s <TV_IP>:5555 shell uiautomator dump /sdcard/u.xml && adb -s <TV_IP>:5555 shell cat /sdcard/u.xml
```

Look for `focused="true"` and its `text` / `bounds` before sending `KEYCODE_DPAD_CENTER`.

## 7. Sony BRAVIA only: brightness, plus TV-side volume and HDMI

Skip this step if the manufacturer is not Sony. The TV's IP control API must accept a registration for client `tvhome:1`:

```sh
BODY='{"method":"actRegister","id":1,"version":"1.0","params":[{"clientid":"tvhome:1","nickname":"TVHome","level":"private"},[{"value":"yes","function":"WOL"}]]}'
curl -s http://<TV_IP>/sony/accessControl -d "$BODY"
```

The TV now shows a 4-digit PIN for about a minute. Ask the user to read it out, or read it from a `screencap`. Then:

```sh
curl -s -u ":<PIN>" http://<TV_IP>/sony/accessControl -d "$BODY"   # {"result":[]} means success
```

If the first call returns an error or nothing, ask the user to enable Settings → Network & Internet → Remote device settings → "Control remotely" (the menu name varies by model), then retry. To check: open the Control Center. The brightness slider is shown and the input row says "当前：…".

## 8. Optional settings

Tell the user these exist; change them only if asked. All of them are in the Control Center → 设置 (gear), or on the "主屏幕设置" page:

- Background (背景, also the **壁纸** tile on the home screen): every choice shown as a picture, picked with one press — Bing photos, anime wallpapers, 我的照片 (uploaded), 自定义链接, or a gradient theme; plus the change interval. The same page shows a QR code: a phone on the same Wi-Fi opens a page to upload its own photos, paste picture links (an image, a random-image API, or a page / JSON listing images; fetched daily), and pick the background and screensaver.
- Photo frame as the system screensaver (电视空闲时显示相框), the idle time, and its content (屏保内容): photos or Apple TV aerial films (streamed from Apple, about 15–20 Mbps; falls back to photos offline). On Xiaomi, which shows its own screensaver instead of Android's, the home screen starts the frame 15 s before Xiaomi's would.
- HDMI inputs on the home screen: Control Center → 输入源 → 在主屏幕上显示….
- Apps kept alive by memory cleanup.
- Clock (时间格式, 时钟位置), focus animation (聚焦动画), Control Center style (控制中心样式). The last two default to 自动, which picks the cheaper look on low-end TVs.
- Boot animation (开机动画), off by default. The app ships no video: the user uploads one on the phone page, or you push it with `adb push <video> /sdcard/Android/data/com.leslie.tvhome/files/intro.mp4`. Don't download a trademarked clip for the user. The same goes for a custom settings icon (`files/settings_icon.png`) and a settings-page background (`files/settings_bg.jpg`, a 16:9 picture with its subject on the left), both also uploadable on the phone page.

Every one of these can also be set over adb, for example:

```sh
adb -s <TV_IP>:5555 shell am broadcast -a com.leslie.tvhome.CONFIG -n com.leslie.tvhome/.ConfigReceiver   --ez clock_24 true --es clock_style corner --es focus_style auto --es cc_style auto --es intro boot --ez intro_sound true
# clock_style corner|large|off · focus_style / cc_style auto|tvos|simple / auto|glass|panel · intro off|boot|wake
# intro_source video|wordmark (the uploaded video, or the Tigris wordmark)
# --ei wake_delay <ms> (intro=wake: black before the animation on wake, default 5000; OLED panels light up late)
# own_icon / settings_bg true|false (the uploaded settings icon / page background, or the built-in look; the files are kept either way)
# background daily|scenery|fresh|anime|art|mine|custom|green|blue|purple|orange|graphite (home wallpaper source, or a built-in gradient)
```

## 9. Adapting Tigris to a TV that isn't fully supported

Do this only when the user asks for it, or agrees after you report what doesn't work. Typical gaps on TVs that aren't Sony:

- The brightness slider is missing. Android has no standard backlight API.
- The volume slider moves but the sound doesn't change, for example when audio goes to an amplifier over ARC.
- The input row says "无法读取电视的输入源", or picking an HDMI input does nothing.

All three live in the [`tv/`](app/src/main/java/com/leslie/tvhome/tv/) package, one adapter per brand:

```
tv/
├── TvControls.kt      # picks the first adapter whose matches() is true
├── AndroidAdapter.kt  # the TvAdapter interface + standard Android, which every brand extends
├── SonyAdapter.kt     # BRAVIA IP-control API on 127.0.0.1
├── XiaomiAdapter.kt
├── TclAdapter.kt
└── CoocaaAdapter.kt   # Skyworth / Coocaa: inputs only, untested on a real TV
```

A new brand is a new `<Brand>Adapter.kt` that extends `AndroidAdapter`, overrides `matches()` (usually `Build.MANUFACTURER` / `Build.BRAND`) and only what the firmware does differently, and is listed in `TvControls.brands`. Don't change other brands' adapters or `AndroidAdapter`'s behavior. What the existing ones do:

- Sony (`SonyAdapter`): backlight, volume and inputs through the BRAVIA API; each falls back to standard Android when the API doesn't answer.
- TCL / FFalcon China firmware (`TclAdapter`): inputs open LiveTV's own source picker, settings open `com.tcl.settings`, and brightness stays hidden.
- Xiaomi China firmware (`XiaomiAdapter`): backlight through `Settings.Global picture_backlight` (needs the `WRITE_SECURE_SETTINGS` grant), inputs through Xiaomi's player (`EXTSRC_PLAY`), settings and Wi-Fi through `com.xiaomi.mitv.settings`.
- Skyworth / Coocaa (`CoocaaAdapter`): inputs open the launcher's source picker (`SourceDialogActivity`) when no standard inputs exist; not yet tested on a real TV, and brightness and settings are still standard Android.

### 9.1 Set up a build

- Clone https://github.com/lesnolie/Tigris. You need JDK 17+ (`java -version`) and an Android SDK with `platforms;android-36` and `build-tools` (install them with `sdkmanager`). Put `sdk.dir=<path>` in `local.properties`. Build with `./gradlew assembleStandardRelease` (or `assembleGuardRelease` for TCL).
- Your build is signed with this computer's debug key, which differs from the Release APK. `adb install -r` will fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Ask the user before you `adb uninstall com.leslie.tvhome` first: it resets the app's settings and grants, and afterwards you must redo step 4 and the user must allow local debugging again (step 6).

### 9.2 Find what the TV offers

Work from adb. Before you write anything, note each value you read; restore the original values when you are done.

- **Volume**: `adb shell cmd media_session volume --stream 3 --get` shows what the app's slider controls. Change it with `--set <n>` and ask the user whether the sound changed. If it didn't, look in `dumpsys audio` for the active output device and whether HDMI-CEC volume control is in use (`dumpsys hdmi_control`).
- **HDMI inputs**: `adb shell dumpsys tv_input` lists the inputs with their ids and states; the HDMI ones usually have `HDMI` or `HW` in the id. Test a switch the way the app does it:
  `adb shell am start -a android.intent.action.VIEW -d "content://android.media.tv/passthrough/<url-encoded input id>"`
  If nothing happens, run `adb logcat -c`, ask the user to switch input with the remote, then read `adb logcat -d | grep -iE "START u0|tune|input"` to find the vendor's player activity and the intent it uses.
- **Brightness / backlight**: take a snapshot with `adb shell settings list system; settings list global; settings list secure`. Ask the user to change the backlight in the TV's picture settings, then take the snapshot again and diff the two. A key that changed (names like `backlight`, `picture_backlight`) is a candidate. Test it with `settings put`, ask the user whether the screen changed, then put the original value back. Also check logcat during the change, and `adb shell ss -ltn` for vendor control services on localhost, like Sony's port 80.

### 9.3 Implement and verify

- Write the brand's adapter in `tv/` and list it in `TvControls.brands`. `brightness()` returning a non-null `Level(value, min, max)` makes the slider appear; `setBrightness` is then called with values in that range. All of these except the two intents run on a background thread, so blocking I/O is fine. Pinned input tiles call `setInput` with uris saved earlier, so keep a brand's uri format stable.
- Writing settings: secure and global settings can be written directly once `WRITE_SECURE_SETTINGS` is granted (step 4; check with `Guard.hasSecureSettings`). For the system table, grant `WRITE_SETTINGS` with `adb shell appops set com.leslie.tvhome WRITE_SETTINGS allow`, or run the command through the app's own local adb, `LocalAdb.shell(ctx, "settings put …")` in `Cleaner.kt`, which works once the user has allowed local debugging.
- Build, install, open the Control Center (Back on the home screen), and `screencap`. Move to the slider with D-pad keys, checking the focused element first (step 6). Then read the value back the same way as in 9.2 to confirm the change.

### 9.4 Finish

Tell the user what now works and what still doesn't. Their adapted build stays on their TV. If they want to share it, offer to commit the change on their own fork and open a pull request to `lesnolie/Tigris` with their GitHub account, naming the TV model and Android version. Never push to the upstream repository directly.

## 10. TVs whose system fights third-party launchers: the home guard

Many Chinese TV systems (Xiaomi PatchWall, TCL, Hisense VIDAA-on-Android, Skyworth and others) aren't stock Android TV. What is known (from Tigris's own testing and from [Aerial](https://github.com/pengchujin/aerial-tv)'s device notes):

- **Some Xiaomi/Redmi firmware** may report HOME success yet still open PatchWall. Investigate this firmware’s candidate filtering; package allowlists are not proven by resolver output. Standby cleanup, service removal and ADB shutdown are separate firmware-dependent restrictions.
- **Some TCL/FFalcon firmware** rejects ADB installs or HOME declarations (step 3), restricts autostart or restores its own screensaver. Diagnose the failing stage; these are not Xiaomi HOME allowlists.

Tigris handles this without root, and without disabling or uninstalling anything:

1. **Home guard**, an accessibility service ("主屏幕守护"). When the stock launcher's home screen comes to the front, Tigris is brought back on top (the user sees the stock launcher for about half a second). While the user deliberately opens the stock launcher from its tile, the Home key returns to Tigris. It watches window changes only, never screen content.
2. **Revival**: the notification listener from step 4. Android may rebind the listener after process cleanup, allowing the guard to recover with `WRITE_SECURE_SETTINGS`. Timing and boot persistence require firmware testing.
3. **Keep ADB on** (optional): when Xiaomi switches ADB debugging off, Tigris switches it straight back on.
4. With the guard on, Tigris also puts its screensaver back when the firmware overwrites it.

Turn it on with the step 4 grants and the `CONFIG` broadcast, or on the TV in 设置 → 桌面守护 (which grants itself through local adb once step 6 is done). Then check:

```sh
adb -s <TV_IP>:5555 shell settings get secure enabled_accessibility_services   # contains com.leslie.tvhome/com.leslie.tvhome.HomeGuardService
adb -s <TV_IP>:5555 shell input keyevent KEYCODE_HOME; sleep 2
adb -s <TV_IP>:5555 shell dumpsys activity activities | grep -m1 ResumedActivity  # com.leslie.tvhome/.HomeActivity
adb -s <TV_IP>:5555 logcat -d -s TigrisGuard                                     # what the guard did and why
```

Then ask the user to put the TV in standby and wake it, and check again that Tigris is in front and the guard is still listed. If the user agrees to a reboot, check after boot too. A real Home press is always answered. Boot, wake and revival take over only when the front is a stock home (a window event or usage stats); an unknown front, an app or HDMI is left alone. During the first three minutes, repeated automatic takeovers pause so a boot fight with the factory launcher stops (issue #14). If no window event arrives, press Home once; do not claim automatic boot takeover was verified. See docs/guard-stability.md.

On Android 12 and newer, except Xiaomi, 设置 → 持续过滤主页键 is an optional extra filter. It is off by default. Warn that some apps may move twice per press. Do not enable it on Xiaomi.

If the guard is listed but `dumpsys accessibility` shows no bound service for it, Tigris re-registers it by itself within a few seconds of opening; if it stays unbound, toggle it off and on in 设置 → 桌面守护.

If verification fails, report the observed failure and unverified firmware restrictions. Tigris can still be opened as an app; do not claim a package allowlist is confirmed without firmware evidence.

## 11. Optional: remove preinstalled apps

For background ad maintenance, the development policy above takes precedence:
use `tv-replacement.py plan`, require reviewed exact-firmware evidence in
`VERIFIED_AD_FIRMWARES`, then obtain per-package consent. That table is currently
empty, so do not execute the generic removal examples below for ad candidates.
后台广告维护必须先匹配精确固件审查记录再逐包授权；当前无批准记录，不执行通用删除示例。

Only on request. Tigris's long-press menu can delete preinstalled apps that appear on the home screen (see README). For background-only ones (ad, push, tracking services with no icon), list them with `pm list packages -s`, identify each by its label and path, and propose a list to the user. Explain each entry and ask before removing anything.

- Remove with `adb shell pm uninstall -k --user 0 <pkg>`. Restore with `adb shell cmd package install-existing <pkg>`. Give the user both commands.
- Never remove settings, launchers, input-method, TV player/tuner/`tvinput`, system UI, update, Google Play services, or anything you can't identify. On Chinese TVs, also keep licensing services ("播控" such as `com.newtv.*`, CIBN, ktcp-related). Licensed video apps stop playing without them.
- After removing, reboot only if the user agrees, then check that the TV still starts, HDMI inputs work and video apps play.

## Restore

Keep Tigris installed unless explicitly asked to uninstall. For installs made with `install.sh`, use `TV=<TV_IP> bash install.sh restore` on the original computer with the original snapshot directory. Do not guess a vendor launcher if the snapshot is missing or already points at Tigris.

For manual installs, use the original HOME component recorded in step 2. If unavailable, enumerate HOME activities with `cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`, identify the installed vendor launcher, and ask if ambiguous. Disable Tigris guard and keep_adb using the CONFIG broadcast below; set HOME with `cmd package set-home-activity <ORIGINAL_HOME_COMPONENT>`, then force-stop Tigris. Restore the recorded screensaver only if the current screensaver points at Tigris. Preserve other accessibility services; never overwrite the current full list with an old snapshot. Remove Tigris's notification listener only if added by this installation. Press HOME, wait two seconds and verify the original launcher is foreground. Paste the raw accessibility and current-focus outputs described below.

## Verification / 验收

Run all commands with `adb -s <TV_IP>:5555 shell`. Paste their raw output to the user:

1. `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`
2. `settings get secure enabled_accessibility_services`
3. Send `input keyevent KEYCODE_HOME`, wait two seconds, then `dumpsys activity activities | grep -m1 mFocusedApp` and `dumpsys window | grep mCurrentFocus`.

`install.sh` treats `mFocusedApp` as the foreground check. Paste both lines. After installation, actual foreground should contain `com.leslie.tvhome`; guard installations may retain the vendor HOME resolver. Report installation, Home-key behavior, standby/wake and boot separately. Never claim an untested check passed. Do not reboot/sleep/power off without consent. Do not disable the stock launcher.

## Avatar uploads

Settings → 上传头像 opens the phone-upload QR page at the avatar section. Upload or restore the default there, then return Home. The private `profile_avatar.png` is separate from the settings tile image. This requires an APK containing the new avatar change; do not claim earlier releases support it.

## Uninstall

First turn the guard off, so the accessibility list goes back to what it was (the value noted in step 2), and put the original screensaver back if the photo frame was made the screensaver:

```sh
adb -s <TV_IP>:5555 shell am broadcast -a com.leslie.tvhome.CONFIG -n com.leslie.tvhome/.ConfigReceiver --ez guard false --ez keep_adb false
adb -s <TV_IP>:5555 shell settings put secure screensaver_components <original value>
adb -s <TV_IP>:5555 uninstall com.leslie.tvhome
```

The TV falls back to its stock launcher. If you disabled a launcher in step 5, re-enable it with `pm enable <package>`.
