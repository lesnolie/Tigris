/* Locale-independent values: brand facts, asset paths and shell commands.
   Two OS variants are generated from the same data so neither can drift:

   - `posix`  : zsh / bash on macOS and Linux. `curl` is the real binary.
   - `powershell` : Windows PowerShell 5.1+ and PowerShell 7. `curl` is an
     alias for Invoke-WebRequest, so the download uses `curl.exe`. Variable
     assignments use `$name = "value"` and are expanded by PowerShell itself.

   The installer snippets do use a local APK filename variable because versioned
   releases change the filename with each tag. They always spell out the app's
   package name; selected snippets must be copied as complete blocks. */

export const brand = {
  name: "TIGRIS",
  product: "Tigris",
  tagline: "Android TV",
  site: "https://tigris-tv.pages.dev",
  repo: "https://github.com/lesnolie/Tigris",
  releases: "https://github.com/lesnolie/Tigris/releases",
  // The Releases list exposes both APK flavors and never needs a website update
  // when a new version ships.
  apk: "https://github.com/lesnolie/Tigris/releases",
  apkGuard: "https://github.com/lesnolie/Tigris/releases",
  downloadHelper: "https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/download-release.py",
  agents: "https://github.com/lesnolie/Tigris/blob/main/AGENTS.md",
  readme: "https://github.com/lesnolie/Tigris/blob/main/README.md",
  issues: "https://github.com/lesnolie/Tigris/issues",
  license: "https://github.com/lesnolie/Tigris/blob/main/LICENSE",
  notices:
    "https://github.com/lesnolie/Tigris/blob/main/THIRD_PARTY_NOTICES.md",
  tcast:
    "https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/tcast-push.py",
  pkg: "com.leslie.tvhome",
  tvIp: "192.168.x.x", // documented placeholder: replace with the TV's IP
} as const;

/* Authentic captures optimised from TVHome/docs/screenshots by
   scripts/build-assets.py. Never replace these with generated images. */
export const shots = {
  home: { src: "/img/home-1280.webp", small: "/img/home-640.webp" },
  shelf: { src: "/img/shelf-1280.webp", small: "/img/shelf-640.webp" },
  grid: { src: "/img/grid-1280.webp", small: "/img/grid-640.webp" },
  cc: { src: "/img/cc-1280.webp", small: "/img/cc-640.webp" },
  frame: { src: "/img/frame-1280.webp", small: "/img/frame-640.webp" },
} as const;

/* Real screen recordings (adb screenrecord on the same Sony BRAVIA), 1280x720 H.264 without
   sound, each with a poster frame taken from the clip itself. Never replace with rendered video. */
export const clips = {
  aerial: { src: "/video/aerial.mp4", poster: "/img/aerial-poster.webp" },
  shelf: { src: "/video/shelf.mp4", poster: "/img/shelf-poster.webp" },
  updates: { src: "/video/updates.mp4", poster: "/img/updates-poster.webp" },
  patchwall: { src: "/video/domestic-launcher.mp4", poster: "/img/domestic-launcher-poster.webp" },
  home: { src: "/video/home.mp4", poster: "/img/home-poster.webp" },
  intro: { src: "/video/intro.mp4", poster: "/img/intro-poster.webp" },
  settings: { src: "/video/settings.mp4", poster: "/img/settings-poster.webp" },
  cc: { src: "/video/cc.mp4", poster: "/img/cc-poster.webp" },
  /* The promo film: motion graphics around the same recordings, with sound; plays only when asked. */
  trailer: { src: "/video/tigris-ad.mp4", poster: "/img/tigris-ad-poster.webp" },
  /* The 2.0.28 film: clock screen saver, grouped settings and custom icons, recorded on a Sony BRAVIA. */
  latest: { src: "/video/tigris-2.0.28.mp4", poster: "/img/tigris-2.0.28-poster.webp" },
} as const;

export const assets = {
  beforeIllustration: "/img/before-illustration.svg",
  aerialIllustration: "/img/aerial-illustration.svg",
  og: "/img/og.jpg",
  favicon: "/img/favicon.svg",
} as const;

export type Shell = "posix" | "powershell";

const P = brand.pkg;

export interface Cmd {
  posix: string;
  powershell: string;
}

const cmd = (posix: string, powershell: string): Cmd => ({ posix, powershell });

export const cmds = {
  /* Run before installing: these values are what the uninstall step restores. */
  snapshotBeforeInstall: cmd(
    `adb -s 192.168.x.x:5555 shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s 192.168.x.x:5555 shell settings get secure screensaver_components
adb -s 192.168.x.x:5555 shell settings get secure enabled_accessibility_services`,
    `adb -s 192.168.x.x:5555 shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s 192.168.x.x:5555 shell settings get secure screensaver_components
adb -s 192.168.x.x:5555 shell settings get secure enabled_accessibility_services`,
  ),

  connect: cmd(
    `adb connect 192.168.x.x:5555
adb -s 192.168.x.x:5555 get-state`,
    `adb connect 192.168.x.x:5555
adb -s 192.168.x.x:5555 get-state`,
  ),

  installStandard: {
    posix: `curl -fsSL -o download-release.py ${brand.downloadHelper}
APK=$(python3 download-release.py standard)
adb -s 192.168.x.x:5555 install -r "$APK"`,
    powershell: `curl.exe -fsSL -o download-release.py ${brand.downloadHelper}
$apk = python .\\download-release.py standard
adb -s 192.168.x.x:5555 install -r $apk`,
  },

  setHomeAndGrant: cmd(
    `adb -s 192.168.x.x:5555 shell cmd package set-home-activity ${P}/.HomeActivity
adb -s 192.168.x.x:5555 shell input keyevent KEYCODE_HOME
adb -s 192.168.x.x:5555 shell pm grant ${P} android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.x.x:5555 shell cmd notification allow_listener ${P}/${P}.RevivalListener
adb -s 192.168.x.x:5555 shell appops set ${P} SYSTEM_ALERT_WINDOW allow`,
    `adb -s 192.168.x.x:5555 shell cmd package set-home-activity ${P}/.HomeActivity
adb -s 192.168.x.x:5555 shell input keyevent KEYCODE_HOME
adb -s 192.168.x.x:5555 shell pm grant ${P} android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.x.x:5555 shell cmd notification allow_listener ${P}/${P}.RevivalListener
adb -s 192.168.x.x:5555 shell appops set ${P} SYSTEM_ALERT_WINDOW allow`,
  ),

  /* Sony BRAVIA IP-control registration. Both variants prompt for the PIN so
     no secret is pasted into a shell even briefly. */
  braviaRegister: {
    posix: `BODY='{"method":"actRegister","id":1,"version":"1.0","params":[{"clientid":"tvhome:1","nickname":"TVHome","level":"private"},[{"value":"yes","function":"WOL"}]]}'
curl -s http://192.168.x.x/sony/accessControl -d "$BODY"
# The TV now shows a 4-digit PIN. Replace PIN with it and run:
curl -s -u ":PIN" http://192.168.x.x/sony/accessControl -d "$BODY"`,
    powershell: `$body = '{"method":"actRegister","id":1,"version":"1.0","params":[{"clientid":"tvhome:1","nickname":"TVHome","level":"private"},[{"value":"yes","function":"WOL"}]]}'
[System.IO.File]::WriteAllText((Join-Path (Get-Location) 'bravia-register.json'), $body)
curl.exe -s http://192.168.x.x/sony/accessControl --data-binary '@bravia-register.json'
# The TV now shows a 4-digit PIN. Replace PIN with it and run:
curl.exe -s -u ":PIN" http://192.168.x.x/sony/accessControl --data-binary '@bravia-register.json'`,
  },

  tclPush: {
    posix: `curl -fsSL -o download-release.py ${brand.downloadHelper}
APK=$(python3 download-release.py guard)
curl -fsSL -o tcast-push.py ${brand.tcast}
python3 tcast-push.py 192.168.x.x "$APK"`,
    powershell: `curl.exe -fsSL -o download-release.py ${brand.downloadHelper}
$apk = python .\\download-release.py guard
curl.exe -fsSL -o tcast-push.py ${brand.tcast}
python .\\tcast-push.py 192.168.x.x $apk`,
  },

  tclGrants: cmd(
    `adb -s 192.168.x.x:5555 shell pm grant ${P} android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.x.x:5555 shell cmd notification allow_listener ${P}/${P}.RevivalListener
adb -s 192.168.x.x:5555 shell appops set ${P} SYSTEM_ALERT_WINDOW allow
adb -s 192.168.x.x:5555 shell appops set ${P} APP_AUTO_START allow
adb -s 192.168.x.x:5555 shell appops set ${P} APP_ASSOC_START allow
adb -s 192.168.x.x:5555 shell am broadcast -a ${P}.CONFIG -n ${P}/.ConfigReceiver --ez guard true`,
    `adb -s 192.168.x.x:5555 shell pm grant ${P} android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.x.x:5555 shell cmd notification allow_listener ${P}/${P}.RevivalListener
adb -s 192.168.x.x:5555 shell appops set ${P} SYSTEM_ALERT_WINDOW allow
adb -s 192.168.x.x:5555 shell appops set ${P} APP_AUTO_START allow
adb -s 192.168.x.x:5555 shell appops set ${P} APP_ASSOC_START allow
adb -s 192.168.x.x:5555 shell am broadcast -a ${P}.CONFIG -n ${P}/.ConfigReceiver --ez guard true`,
  ),

  tclCheck: cmd(
    `adb -s 192.168.x.x:5555 shell settings get secure enabled_accessibility_services
adb -s 192.168.x.x:5555 shell input keyevent KEYCODE_HOME
adb -s 192.168.x.x:5555 shell dumpsys activity activities | grep -m1 ResumedActivity`,
    `adb -s 192.168.x.x:5555 shell settings get secure enabled_accessibility_services
adb -s 192.168.x.x:5555 shell input keyevent KEYCODE_HOME
adb -s 192.168.x.x:5555 shell "dumpsys activity activities | grep -m1 ResumedActivity"`,
  ),

  packagePath: cmd(
    `adb -s 192.168.x.x:5555 shell pm path ${P}`,
    `adb -s 192.168.x.x:5555 shell pm path ${P}`,
  ),

  xiaomiSetup: cmd(
    `adb -s 192.168.x.x:5555 shell cmd package set-home-activity ${P}/.HomeActivity
adb -s 192.168.x.x:5555 shell pm grant ${P} android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.x.x:5555 shell cmd notification allow_listener ${P}/${P}.RevivalListener
adb -s 192.168.x.x:5555 shell appops set ${P} SYSTEM_ALERT_WINDOW allow`,
    `adb -s 192.168.x.x:5555 shell cmd package set-home-activity ${P}/.HomeActivity
adb -s 192.168.x.x:5555 shell pm grant ${P} android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.x.x:5555 shell cmd notification allow_listener ${P}/${P}.RevivalListener
adb -s 192.168.x.x:5555 shell appops set ${P} SYSTEM_ALERT_WINDOW allow`,
  ),

  xiaomiGuard: cmd(
    `adb -s 192.168.x.x:5555 shell am broadcast -a ${P}.CONFIG -n ${P}/.ConfigReceiver --ez guard true --ez keep_adb true`,
    `adb -s 192.168.x.x:5555 shell am broadcast -a ${P}.CONFIG -n ${P}/.ConfigReceiver --ez guard true --ez keep_adb true`,
  ),

  stockInstall: {
    posix: `curl -fsSL -o download-release.py ${brand.downloadHelper}
APK=$(python3 download-release.py standard)
adb -s 192.168.x.x:5555 install -r "$APK"
adb -s 192.168.x.x:5555 shell cmd package set-home-activity ${P}/.HomeActivity
adb -s 192.168.x.x:5555 shell input keyevent KEYCODE_HOME`,
    powershell: `curl.exe -fsSL -o download-release.py ${brand.downloadHelper}
$apk = python .\\download-release.py standard
adb -s 192.168.x.x:5555 install -r $apk
adb -s 192.168.x.x:5555 shell cmd package set-home-activity ${P}/.HomeActivity
adb -s 192.168.x.x:5555 shell input keyevent KEYCODE_HOME`,
  },

  verify: cmd(
    `adb -s 192.168.x.x:5555 shell screencap -p /sdcard/tigris-check.png
adb -s 192.168.x.x:5555 pull /sdcard/tigris-check.png tv.png`,
    `adb -s 192.168.x.x:5555 shell screencap -p /sdcard/tigris-check.png
adb -s 192.168.x.x:5555 pull /sdcard/tigris-check.png tv.png`,
  ),

  uninstall: cmd(
    `adb -s 192.168.x.x:5555 shell am broadcast -a ${P}.CONFIG -n ${P}/.ConfigReceiver --ez guard false --ez keep_adb false`,
    `adb -s 192.168.x.x:5555 shell am broadcast -a ${P}.CONFIG -n ${P}/.ConfigReceiver --ez guard false --ez keep_adb false`,
  ),

  restoreCheck: cmd(
    `curl -fsSL -o check-install.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/check-install.py
python3 check-install.py verify --serial 192.168.x.x:5555 --expected ORIGINAL_PACKAGE/ORIGINAL_ACTIVITY`,
    `curl.exe -fsSL -o check-install.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/check-install.py
python check-install.py verify --serial 192.168.x.x:5555 --expected ORIGINAL_PACKAGE/ORIGINAL_ACTIVITY`,
  ),

  uninstallPackage: cmd(
    `adb -s 192.168.x.x:5555 uninstall ${P}`,
    `adb -s 192.168.x.x:5555 uninstall ${P}`,
  ),

  reenableVendor: cmd(
    `adb -s 192.168.x.x:5555 shell pm enable <VENDOR_LAUNCHER_PACKAGE>`,
    `adb -s 192.168.x.x:5555 shell pm enable <VENDOR_LAUNCHER_PACKAGE>`,
  ),

  checkHome: cmd(
    `curl -fsSL -o check-install.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/check-install.py
python3 check-install.py verify --serial 192.168.x.x:5555 --report verification.json`,
    `curl.exe -fsSL -o check-install.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/check-install.py
python check-install.py verify --serial 192.168.x.x:5555 --report verification.json`,
  ),
} as const;

/* Kept for callers that only care about the shell-agnostic text. */
export const pick = (c: Cmd, shell: Shell) => c[shell];

export const shellMeta: Record<Shell, { label: string; hint: string }> = {
  posix: { label: "macOS / Linux", hint: "" },
  powershell: { label: "Windows PowerShell", hint: "" },
};
