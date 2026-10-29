import { brand, cmds } from "./site";
import type { Copy } from "./lang";

const C: typeof cmds = cmds;

export const en = {
  skip: "Skip to content",
  navLabel: "Main",
  menuLabel: "Open menu",
  menuCloseLabel: "Close menu",
  nav: [
    { href: "#features", label: "Features" },
    { href: "#aerial", label: "Aerial" },
    { href: "#compare", label: "Compare" },
    { href: "#install", label: "Install" },
    { href: "#webos", label: "LG webOS" },
    { href: "#compatibility", label: "Compatibility" },
    { href: "#faq", label: "FAQ" },
  ],
  langSwitch: { href: "/", label: "中文", lang: "zh-CN" },
  headerCta: "Downloads",
  menuCta: "Download Tigris APK",
  menuNote: "Android TV 9+ · no root · independent project, not affiliated with Apple",
  copy: "Copy",
  copied: "Copied",
  copyFailed: "Copy failed",
  compareLabel: "Compare a user-supplied TV screenshot with a Tigris TV recording",
  compareValue: (p: number) => `Showing ${p}% of the before image`,
  slideshow: { prev: "Previous", next: "Next" },
  lightbox: { close: "Close", prev: "Previous", next: "Next", name: "Screenshot viewer" },
  year: 2026,

  meta: {
    title: "Tigris — a tvOS-inspired Android TV launcher, with Apple TV aerials",
    description:
      "Tigris is an open-source Android TV launcher: liquid-glass dock, Top Shelf content, frosted Control Center, Apple TV aerial screensaver and landscape wallpapers. Android 9+, no root, verified on Sony BRAVIA.",
    canonical: `${brand.site}/en/`,
    alts: [
      { hreflang: "zh-CN", href: `${brand.site}/` },
      { hreflang: "en", href: `${brand.site}/en/` },
      { hreflang: "x-default", href: `${brand.site}/` },
    ],
    ogLocale: "en_US",
    ogLocaleAlt: "zh_CN",
    ogTitle: "Tigris — a tvOS-inspired Android TV launcher",
    ogDescription:
      "Liquid-glass dock, Top Shelf, Control Center and Apple TV aerial screensavers. Open source, Android 9+, no root.",
  },

  hero: {
    eyebrow: "TIGRIS · ANDROID TV LAUNCHER",
    titleLines: ["Your TV home screen,", "worth opening again."],
    sub: "Turn the Android TV you already own into something closer to tvOS: a sunken liquid-glass dock, a Top Shelf that actually plays something, and Apple TV aerial films drifting across the screen when the room goes quiet.",
    primary: "Download",
    secondary: "See how to install",
    chips: ["Android TV 9+", "No root", "Some features vary by brand"],
    note: "An independent open-source project, not affiliated with Apple. “Apple TV” is a trademark of Apple Inc.",
    imageAlt:
      "Tigris home screen on a Sony BRAVIA TV: a landscape photo with a glass dock of app tiles.",
    floating: [
      { title: "Liquid Glass", sub: "Sunken glass dock" },
      { title: "Aerial screensaver", sub: "The Apple TV aerial films" },
    ],
  },

  proof: [
    "Verified on Sony BRAVIA",
    "Apple TV aerial screensaver",
    "Standard and guard APKs",
    "No root, fully reversible",
  ],

  compare: {
    eyebrow: "BEFORE / AFTER",
    title: "Drag to reveal the difference.",
    lede: "User-supplied Chinese-market <strong>TV launcher screenshot</strong> on the left. A <strong>Tigris recording</strong> on the right. Drag the divider to compare the two interfaces.",
    beforeLabel: "Before",
    beforeSub: "TV screenshot · animated view",
    afterLabel: "After",
    afterSub: "Tigris · TV recording",
    beforeAlt: "User-supplied Chinese-market TV screenshot with a gentle pan and zoom.",
    afterAlt: "Tigris home screen, real capture.",
    hintKeys: "Drag, click, or focus the handle and use ← →. Hold Shift for larger steps; double-click to reset.",
    disclaimer: "The left side animates a user-supplied TV screenshot; it is not an interaction recording. These are different devices.",
  },

  aerial: {
    eyebrow: "AERIAL SCREENSAVER",
    title: "Apple TV aerials, as your screensaver.",
    lede: "When the TV goes quiet it drifts over the planet instead: coastlines, glaciers and city lights unfold with the location name following each shot. Use the arrow keys to change scene; offline it falls back to photos, and it never interrupts something already playing.",
    bullets: [
      "Choose aerial films as the screensaver content; they stream from Apple's servers at roughly 15–20 Mbps",
      "Offline or on a failed buffer it falls back to the photo frame, without blocking the next idle transition",
      "After 1–30 minutes of idle time the photo frame appears, with the clock and caption moving periodically to protect OLED panels",
    ],
    caption: "Photo frame · Bing daily photograph (photo mode shown; aerial films are video)",
    frameAlt: "Photo frame screensaver: an underwater octopus photograph with the time and date in the corner.",
    bulletsTitle: "How to enable it",
    attribution:
      "Aerial films stream from Apple's servers; Tigris ships no video files. “Apple TV” is a trademark of Apple Inc. This project is not affiliated with Apple.",
  },

  features: {
    eyebrow: "FEATURES",
    title: "Designed for ten feet, not for a phone.",
    lede: "Bigger focus, fewer layers, quieter glass — laid out again for the room instead of scaled up from a handset.",
  },

  film: {
    eyebrow: "FILM",
    title: "Tigris in 42 seconds.",
    lede: "See how Tigris gives your TV home screen a fresh start. Press play with sound on.",
    label: "Play the Tigris film (with sound)",
  },
  gallery: {
    eyebrow: "IN MOTION",
    title: "See the details in motion.",
    lede: "Home, Control Center and aerial clips were recorded on a Sony BRAVIA. The startup clip previews the owner-supplied default asset. Select a clip to enlarge.",
  },

  install: {
    eyebrow: "INSTALL",
    title: "Choose your TV.",
    lede: "Tap one brand below.",
    packages: [
      {
          name: "Standard APK",
          use: "First choice on Android 9+ Sony BRAVIA, stock Android TV / Google TV, Xiaomi / Redmi and other compatible Android TVs. Use Tigris-vX.Y.Z.apk. Successful installation alone does not establish physical HOME takeover.",
        href: brand.apk,
      },
      {
          name: "Compatibility Guard APK",
          use: "For restricted Android firmware, especially TCL / FFalcon builds that reject Standard. Declares no HOME role and requires authorized Guard. Choose Tigris-guard-vX.Y.Z.apk; some TCL TVs need TV-side T-Cast installation. Not all TCL TVs need Guard.",
        href: brand.apkGuard,
      },
    ],
    prereq:
      "Before you start: the TV is on the same network as the computer, ADB debugging is enabled, and the first connection shows “Allow USB debugging?” — tick “Always allow”. The computer needs Android SDK Platform-Tools.",
    shellNote:
      "Choose your computer system for setup instructions, then choose your TV brand. Commands below adapt to your selection. Run them in order.",
    shellLabels: { posix: "macOS / Linux", powershell: "Windows PowerShell" },
    placeholderNote:
      "192.168.x.x is a placeholder for your TV's IP address. In the BRAVIA step, PIN is the four-digit code shown on the TV; type it in place of the placeholder.",
    adbTable: {
      caption: "Enabling ADB debugging by brand",
      head: ["TV", "How to enable ADB"],
      rows: [
        [
          "Xiaomi / Redmi",
          "Settings → About → <strong>Product model</strong>: press OK until developer mode is on. Then Settings → Accounts &amp; security: enable <strong>ADB debugging</strong> and <strong>Install unknown apps</strong>.",
        ],
        [
          "TCL / FFalcon",
          "Settings → About → <strong>Device info</strong>: press <strong>Up, Down, Left, Right</strong> within one second. A <strong>More</strong> group appears; enable <strong>ADB</strong>.",
        ],
        [
          "Sony BRAVIA",
          "Settings → System → About → press OK on <strong>Build</strong> seven times. Then Settings → System → Developer options → <strong>USB debugging</strong>.",
        ],
        [
          "Stock Android TV / Google TV",
          "Settings → Device Preferences → About → press <strong>Build</strong> seven times. Then enable <strong>USB debugging</strong> (sometimes “Network debugging”).",
        ],
      ],
    },
    tabsLabel: "Choose your TV",
    tabs: ([
      {
        id: "sony",
        label: "Sony BRAVIA",
        intro:
          "The fully verified path, including brightness, TV volume and HDMI input status. Replace 192.168.x.x with the TV's IP.",
        steps: [
          { title: "Connect", body: "Accept “Allow USB debugging?” on the TV and tick “Always allow”.", cmd: C.connect },
          {
            title: "Record the original home, screensaver and accessibility services",
            body: "You need them if you ever uninstall; save the output somewhere safe.",
            cmd: C.snapshotBeforeInstall,
          },
          { title: "Download and install the standard build", cmd: C.installStandard, note: "Commands match the computer system selected above." },
          {
            title: "Make it the home screen and grant permissions",
            body: "The three grants cover the guard and screensaver, reviving after the system kills the app, and opening the home screen from the background after boot.",
            cmd: C.setHomeAndGrant,
          },
          {
            title: "Optional: register BRAVIA IP control",
            body: "Needed for brightness, TV volume and HDMI input status. The TV shows a four-digit PIN after the first command; use it in the second.",
            cmd: C.braviaRegister,
            note: "Run the registration in two stages; replace PIN after the TV displays it.",
          },
          {
            title: "Allow on-device debugging",
            body: "The first launch asks for on-device debugging, used for standby, power and deep memory cleanup. Tick “Always allow”, then open the Control Center and check the “本机调试未授权” line is gone.",
          },
          { title: "Verify", cmd: C.verify, note: "You should see a compact clock, two round controls and the glass dock." },
        ],
      },
      {
        id: "tcl",
        label: "TCL / FFalcon",
        intro:
          "TCL firmware rejects every adb install and any APK that declares a home screen. Push the guard build through the TV's own T-Cast service instead.",
        danger:
          "Some TCL / FFalcon firmware has no usable backlight interface, so the brightness slider hides, and the input row opens TCL's own source picker. That is a firmware limit, not a failed install.",
        steps: [
          { title: "Connect", cmd: C.connect },
          { title: "Record the original home, screensaver and accessibility services", cmd: C.snapshotBeforeInstall },
          {
            title: "Push the guard build with T-Cast",
            body: "The script serves the APK on local port 8765 and connects to the TV on 6553. When “多屏互动：正在发起应用推送” appears on the TV, choose <strong>同意</strong> (Agree) with the remote.",
            cmd: C.tclPush,
            note: "Commands match your selected system. Allow local-network access to port 8765 if prompted.",
          },
          { title: "Confirm it installed", cmd: C.packagePath },
          {
            title: "Grant permissions and enable the home guard",
            body: "The guard build declares no home screen, so the home guard keeps it in front. TCL blocks third-party autostart by default, so both appops are required.",
            cmd: C.tclGrants,
          },
          {
            title: "Check the guard is running",
            cmd: C.tclCheck,
            note: "The output should list `com.leslie.tvhome/com.leslie.tvhome.HomeGuardService`, and the last line should be `com.leslie.tvhome/.HomeActivity`.",
          },
        ],
      },
      {
        id: "xiaomi",
        label: "Xiaomi / Redmi",
        intro:
          "Xiaomi accepts set-home-activity but the Home key can still return to PatchWall. Keep the stock launcher and use the home guard to bring Tigris back.",
        danger:
          "<strong>Never disable the stock Xiaomi launcher</strong> (com.mitv.tvhome). The TV then boots to a black screen that only ADB can recover.",
        steps: [
          { title: "Connect", cmd: C.connect },
          { title: "Record the original home, screensaver and accessibility services", cmd: C.snapshotBeforeInstall },
          { title: "Install the standard build", cmd: C.installStandard, note: "Commands match the computer system selected above." },
          { title: "Make it the home screen and grant permissions", cmd: C.xiaomiSetup, note: "Run the shared checker first; skip guard when Home already works." },
          {
            title: "Enable guard only after a failed Home check",
            body: "keep_adb true makes Tigris switch ADB back on after Xiaomi turns it off on wake; drop it if you do not want that.",
            cmd: C.xiaomiGuard,
              note: "Only after actual Home failure; keep_adb is a separate optional preference.",
          },
          {
            title: "Check",
            body: "Press Home: the stock launcher may flash for about half a second, then Tigris returns. If the guard is not running, open Settings → 桌面守护 in Tigris (after the on-device debugging step).",
          },
        ],
      },
      {
        id: "stock",
        label: "Stock Android TV",
        intro:
          "The standard path. Tigris becomes the default home screen without modifying the system partition or uninstalling anything.",
        steps: [
          {
            title: "Connect and check the Android version",
            cmd: C.connect,
            note: "Then run `adb -s 192.168.x.x:5555 shell getprop ro.build.version.sdk`; it must be at least 28 (Android 9).",
          },
          { title: "Record the original home, screensaver and accessibility services", cmd: C.snapshotBeforeInstall },
          { title: "Install and set as home", cmd: C.stockInstall, note: "Commands match the computer system selected above." },
          {
            title: "Grant what only ADB can grant",
            cmd: `adb -s 192.168.x.x:5555 shell pm grant com.leslie.tvhome android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.x.x:5555 shell cmd notification allow_listener com.leslie.tvhome/com.leslie.tvhome.RevivalListener
adb -s 192.168.x.x:5555 shell appops set com.leslie.tvhome SYSTEM_ALERT_WINDOW allow`,
          },
          {
            title: "If Home still returns to the stock launcher",
            body: "Run the shared checker: it opens Settings and sends HOME. Configure guard only when needed; keep stock Home enabled.",
            cmd: C.checkHome,
            note: "Test the physical remote, wake and reboot separately on every brand.",
          },
        ],
      },
    ]),
    advanced: {
      title: "Advanced: what those permissions do",
      body: "The three grants in the commands are not available to ordinary apps. They work locally on the device and transmit nothing.",
      rows: [
        { name: "WRITE_SECURE_SETTINGS", use: "Switch the guard back on after the system turns it off, keep the photo frame as screensaver, keep ADB on after Xiaomi wake" },
        { name: "Notification listener (RevivalListener)", use: "Restart Tigris about half a second after the system kills it and at boot; reads no notification" },
        { name: "SYSTEM_ALERT_WINDOW", use: "Open the home screen from the background after boot; no overlays or ads" },
        { name: "TCL APP_AUTO_START / APP_ASSOC_START", use: "TCL only; allows third-party apps to start at boot" },
      ],
    },
    agent: {
      title: "Let an AI agent do it (recommended)",
      body: "Send this message to WorkBuddy, Codex, Claude Code or another desktop agent that can run commands. It should confirm the TV OS and IP, follow the shared strategy, verify installation and preserve recovery.",
      prompt:
        "Install Tigris on my TV. TV IP: <TV_IP>. Read https://tigris-tv.pages.dev/agent.md first (fallback: https://raw.githubusercontent.com/lesnolie/Tigris/main/AGENTS.md). Confirm Android, webOS or another OS before acting; never use ADB/APKs on non-Android systems. For Android, inspect with tools/install-plan.py from v2.0.9 or newer source, then follow the shared strategy. Preserve settings, APK flavor and the factory launcher. Do not automatically uninstall, disable system components or enable keep-ADB. Tell me when TV authorization is needed. Report APK installation, synthetic Home, physical Home, startup, wake and recovery separately; mark untested results unverified and do not promise ad removal.",
      note: "The TV shows two or three permission dialogs; choose “Allow” with the remote. On TCL, choose “同意” when the T-Cast push prompt appears.",
    },
    safety:
      "<strong>Never power off or restart the TV remotely without asking.</strong> A TV that is off can only be turned back on with its remote. Nothing in this install disables or uninstalls a system app.",
  },

  compatibility: {
    eyebrow: "COMPATIBILITY",
    title: "What your TV can actually do.",
    lede: "Tigris tries the brand API first and falls back to standard Android. This is the current boundary, not marketing.",
    caption: "Android TV 9+ · standard and guard builds behave identically apart from the home declaration",
    head: ["Feature", "Any Android TV", "On-device debugging", "Sony BRAVIA only"],
    rows: [
      { feature: "Home screen, dock, glass, shadows", any: "Yes", debug: "—", sony: "—" },
      { feature: "Top Shelf (Bilibili / Douyu / custom)", any: "Matching TV app, or a JSON URL", debug: "—", sony: "—" },
      { feature: "Landscape wallpapers, photo frame", any: "Yes", debug: "—", sony: "—" },
      { feature: "Apple TV aerial screensaver", any: "Yes, needs internet", debug: "—", sony: "—" },
      { feature: "Control Center, clock", any: "Yes", debug: "—", sony: "—" },
      { feature: "Memory cleanup", any: "Cache only", debug: "Kills background apps", sony: "—" },
      { feature: "Remove preinstalled apps", any: "—", debug: "Yes", sony: "—" },
      { feature: "Standby, power off, restart", any: "—", debug: "Yes", sony: "—" },
      { feature: "Frame as system screensaver, idle time", any: "—", debug: "Yes", sony: "—" },
      { feature: "Volume slider", any: "Android standard", debug: "—", sony: "TV volume" },
      { feature: "HDMI input switching", any: "Most TVs", debug: "—", sony: "Plus input status" },
      { feature: "Brightness slider", any: "Xiaomi, after ADB grant", debug: "—", sony: "Yes" },
      { feature: "Home guard, revival after kill", any: "One-time ADB grant", debug: "Yes", sony: "—" },
    ],
    notes: [
      { title: "On-device debugging", body: "Tigris talks to the TV's own 127.0.0.1:5555 to run system commands. The TV asks “Allow debugging?”; tick “Always allow”. The same mechanism works on every brand." },
      { title: "Sony BRAVIA", body: "After IP-control registration, brightness, volume and inputs use the TV's own API and the input row can show the current source." },
      { title: "TCL / FFalcon China firmware", body: "No standard input registration, so the input row opens TCL's picker; no usable backlight interface, so the brightness slider hides; needs the guard build." },
      { title: "Xiaomi / Redmi China firmware", body: "Backlight through picture_backlight and inputs through Xiaomi's player; never disable the stock launcher, use the home guard." },
      { title: "Coocaa / Skyworth", body: "When no standard inputs are registered, the input row opens the Coocaa launcher's own source picker; brightness and settings stay on standard Android. Not yet verified on a real TV." },
      { title: "Other brands", body: "Volume through AudioManager and inputs through TvInputManager. Android has no standard backlight API, so the brightness slider hides. Each brand is one adapter file in tv/; pull requests for your TV are welcome." },
      { title: "Receivers / ARC · eARC", body: "Standard volume may control the TV rather than the receiver; CEC volume behaviour depends on the TV and receiver firmware." },
    ],
    scope:
      "<strong>Verified scope:</strong> fully tested only on a Sony BRAVIA XR-55A80J (Android TV 12). TCL / FFalcon and Xiaomi support is ported from community device notes and has not been verified on every firmware.",
  },

  faq: {
    eyebrow: "FAQ",
    title: "Before you install.",
    items: [
      {
        q: "Does it need root? Will it void my warranty?",
        a: [
          "No root and no system partition changes. The standard build only sets itself as the default home screen through ADB and requests a few permissions an ordinary app cannot get.",
          "Warranty terms are up to the manufacturer. The real risk comes from misusing ADB commands, such as disabling the Xiaomi stock launcher.",
        ],
      },
      {
        q: "How do I restore the original launcher?",
        a: [
          "Follow the README restore procedure, which keeps Tigris installed by default. Turn guard off and restore the recorded default HOME. Restore the original screensaver only when the current one belongs to Tigris.",
          "Replace ORIGINAL_PACKAGE/ORIGINAL_ACTIVITY below with the original HOME component. Verify recovery before explicitly choosing to uninstall. If recovery is unverified, keep Tigris installed and do not guess a vendor component.",
          "Keep stock launchers enabled on every brand. Never reboot or put the TV to sleep without consent.",
        ],
        cmds: [
          { text: cmds.uninstall },
          { text: cmds.restoreCheck },
        ],
      },
      {
        q: "Why must I not disable the Xiaomi stock launcher?",
        a: [
          "PatchWall (com.mitv.tvhome) is enforced by a system whitelist, and the fallback home also refuses third-party launchers, so disabling it leaves a black screen.",
          "Tigris uses a home guard instead: when the stock launcher comes to the front, Tigris returns within about half a second. No system app is disabled or removed.",
        ],
      },
      {
        q: "TCL says INSTALL_FAILED_VERIFICATION_FAILURE",
        a: [
          "TCL's verifier rejects every adb install. Use the versioned Guard APK (Tigris-guard-vX.Y.Z.apk) and push it through the TV's T-Cast service; the commands are in the TCL tab above.",
          "If the script cannot reach port 6553, that model has no T-Cast service. Push the APK to /sdcard/Download/ and install it with a file manager on the TV.",
        ],
      },
      {
        q: "Why is there no brightness slider?",
        a: [
          "Android has no standard backlight API. Sony BRAVIA exposes brightness through its own IP-control API, and Xiaomi China firmware through picture_backlight (needs the ADB grant). On other brands the slider hides instead of showing a control that does nothing.",
        ],
      },
      {
        q: "Why is the volume wrong with a receiver on ARC / eARC?",
        a: [
          "The standard Android volume stream may control the TV rather than the receiver. Sony can use the TV-side volume API instead; CEC behaviour ultimately depends on the TV and receiver firmware.",
        ],
      },
      {
        q: "ADB keeps saying unauthorized",
        a: [
          "The TV has not accepted this computer yet. On the TV, tick “Always allow from this computer” and press Allow. Xiaomi turns ADB off on every wake; Tigris's “keep ADB on” option switches it back on (Xiaomi only).",
        ],
      },
      {
        q: "Does the home guard read my screen or notifications?",
        a: [
          "No. It watches window changes only, to notice when the stock launcher comes forward. The notification listener exists solely so Android can rebind Tigris after it is killed, and it reads nothing.",
        ],
      },
      {
        q: "How do I update?",
        a: [
          "Download the latest APK from GitHub Releases and install it with adb install -r over the existing app; settings and layout are kept. Every download entry point opens the full Releases list, so the site does not need updating for each version.",
        ],
      },
      {
        q: "Does the site or the app collect data?",
        a: [
          "The website is static: no cookies, no third-party analytics, no ad scripts, and system fonts only.",
          "The app needs the network for wallpapers (Bing, Wallhaven) and Top Shelf content (public Bilibili and Douyu web endpoints). A custom Top Shelf address is requested only after you enter one, and that request goes from the TV to the address you set. Aerial films stream from Apple's servers. There is no account system.",
        ],
      },
      {
        q: "Is this affiliated with Apple?",
        a: [
          "No. Tigris is an independent open-source project, not affiliated with or endorsed by Apple, Sony, Xiaomi, TCL, Bilibili or Douyu. The design language is inspired by tvOS but includes no Apple code, fonts or assets.",
        ],
      },
    ],
  },

  finalCta: {
    eyebrow: "GET TIGRIS",
    title: "Make every TV moment feel new.",
    body: "Download the APK, enable ADB, and follow the guide or hand it to an agent. Android 9+, no root, reversible.",
    primary: "Download from Releases",
    secondary: "View source on GitHub",
  },

  footer: {
    blurb:
      "The third project in an open-source zodiac series: the tiger. A home screen with tvOS-like glass and Apple TV aerials.",
    cols: [
      {
        title: "Product",
        links: [
          { href: "#features", label: "Features" },
          { href: "#aerial", label: "Aerial screensaver" },
          { href: brand.releases, label: "Releases" },
          { href: brand.repo, label: "GitHub" },
        ],
      },
      {
        title: "Install",
        links: [
          { href: "#install", label: "Guide" },
          { href: "#compatibility", label: "Compatibility" },
          { href: "#faq", label: "FAQ" },
          { href: brand.agents, label: "AGENTS.md" },
        ],
      },
      {
        title: "Project",
        links: [
          { href: brand.readme, label: "README" },
          { href: brand.issues, label: "Issues" },
          { href: brand.license, label: "MIT license" },
          { href: brand.notices, label: "Third-party notices" },
        ],
      },
    ],
    legal:
      "© 2026 Tigris · independent open-source project. Not affiliated with, endorsed by or sponsored by Apple, Sony, Xiaomi, TCL, Bilibili or Douyu. “Apple TV” is a trademark of Apple Inc., named here only to describe the optional aerial screensaver. Third-party artwork, photos and app icons remain the property of their owners. Aerial films stream from Apple's servers; this project ships no video files. Install and use at your own risk.",
    langLink: { href: "/", label: "中文", lang: "zh-CN" },
  },
} satisfies Copy;
