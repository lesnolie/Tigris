# LG webOS deployment — developer preview

> **LG webOS 测试包 / Developer preview:** [webos-v0.1.0-preview.1](https://github.com/lesnolie/Tigris/releases/tag/webos-v0.1.0-preview.1) · [IPK 安装、启动、更新和卸载 / Setup guide](https://github.com/lesnolie/Tigris/blob/main/docs/webos-preview-install.md)。已提供打包 IPK，无需自行构建；未做实机测试，不是稳定版，不接管 Home。Prebuilt IPK available; hardware untested, no Home takeover.

> **Actual state:** Tigris distributes Android APKs and a separate **webOS test IPK**. The prebuilt preview download is linked above; the script below remains an optional source-build route. There is NO downloadable official stable LG IPK, native Home-key interception, or verified LG physical-TV test yet. Use the tagged preview IPK for testing; source builds are a separate option.

## Two independent capability modules

- `webos/app/platform/no-root.js`: default LG Developer Mode app, Luna app/HDMI launch, and read-only capability status. No Root, no privileged system replacement.
- `webos/app/platform/root.js`: on-demand inspection of **existing** Homebrew root access, plus an opt-in read-only full app inventory via a fixed Luna shell command. No rooting, root exploit, mutation or persistent Home service.
- `webos/app/platform/core.js`: common app/HDMI launch and Luna IPC.
- `webos/app/weather.js`: optional Open-Meteo search, weather refresh and cache.
- `webos/app/runtime.js`: tvOS-inspired lightweight dock, remote keys and settings.

**Why no active Home interception in the first source preview?** Real non-root interception needs a separately packaged native Helper, tested input-event relaying, a hardware watchdog and a verified escape route. The read-only preflight checks *whether we can connect*, not whether input interception succeeds. See [AI/Helper technical contract](../docs/WEBOS_AI_INSTALL.md). Root mode is an additional module, not an excuse to install untested native input code.

## Prerequisites

LG TV: install LG **Developer Mode**, sign in, enable Developer Mode and Key Server. Mac/Windows/Linux: install Node.js and `@webos-tools/cli`, and pair with the TV via `ares-setup-device` and `ares-novacom` (the CLI handles the SSH key). On stock webOS, Dev Mode SSH usually uses **prisoner on port 9922**, **not Android ADB 5555**. Do not share private pairing material with an AI or paste it into GitHub issues.

- LG CLI guide: https://webostv.developer.lge.com/develop/tools/cli-dev-guide
- Developer Mode app: https://webostv.developer.lge.com/develop/getting-started/developer-mode-app

## Let an AI install Tigris (copyable prompt)

> Identify whether my TV runs Android TV or LG webOS **before running any command**. For LG webOS, read `https://github.com/lesnolie/Tigris/blob/main/docs/WEBOS_AI_INSTALL.md` and `webos/README.md` first. Never run ADB, Android package commands, or Android installer on webOS. Confirm official webOS CLI, Developer Mode + Key Server and pairing; ask me to authorize pairing on the TV. Run the **read-only** webOS preflight and show its raw result. If I authorize installation, build the Tigris webOS prototype from the current verified repository checkout and install it with the LG CLI. The Root module may only inspect existing Root upon my request; do not root the TV. Do not enable or claim a Home Helper unless a separate safe native capability probe and a physical Home-key test have both passed. Verify that LG Home still works. Do not print pairing secrets or silently reboot the TV.

## One command (macOS/Linux)

From a cloned repository, after `ares-setup-device` has registered TV alias `myTV`:

```sh
sh webos/install.sh --device myTV --preflight  # only inspect; safe first step
sh webos/install.sh --device myTV --install    # build, install and launch preview
```

For an already published `main` branch, to fetch the install script without cloning:

```sh
curl -fsSLo tigris-webos.sh https://raw.githubusercontent.com/lesnolie/Tigris/main/webos/install.sh && sh tigris-webos.sh --device myTV --install
```

Replace `myTV` with the **registered LG CLI device alias**. Inspect the script before executing. The script fetches **source from main** (not a signed stable IPK) if no local checkout exists. It only installs an ordinary webOS app and will not change the LG Home key.

## Manual installation

```sh
npm install -g @webos-tools/cli
ares-setup-device                   # Add LG TV; follow Developer Mode pairing instructions
ares-setup-device --list
# Enable Key Server on the TV; enter the passphrase locally when prompted.
ares-novacom --device myTV --getkey
ares-device --system-info --device myTV
ares-package webos/app
ares-install --device myTV org.tigris.webos_0.1.0_all.ipk
ares-launch --device myTV org.tigris.webos
```

For Windows use PowerShell with the same `ares-*` commands and a downloaded repository; the POSIX shell one-liner requires a POSIX shell environment. Package name may change with `appinfo.json` version. After install, check app and HDMI launch, remote focus/Back and city weather **only if the user enabled it**. Other APIs may be denied by particular firmware; that requires physical validation. On older webOS versions, CSS/glass features may degrade gracefully.

## Safe restore/uninstall

Developer Mode expires. Renew it in LG's Developer Mode app before expiry; when it is disabled, developer-installed apps may be removed. This is separate from Tigris Home compatibility.

```sh
sh webos/install.sh --device myTV --uninstall
# or:
ares-install --device myTV --remove org.tigris.webos
```

This removal does not modify LG Home. LG Quick Access assignments (if the user created them) are managed from the TV itself. There is no Tigris-native Home Helper to disable in this preview. If future Helper modules are added, stopping and releasing input capture **before uninstall** is mandatory.

## Weather

Default OFF. Search manually for a city and choose it. Open-Meteo API, cached for 30 minutes, last reading usable for up to 6 hours. Turning weather off hides the card and stops weather requests. No IP geolocation, no GPS/location permission, no default city. Open-Meteo license, attribution and commercial-use conditions apply. The same semantics apply to future Android weather module.
