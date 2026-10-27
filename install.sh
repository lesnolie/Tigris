#!/usr/bin/env bash
# Tigris installer. Requires bash 3.2+, adb, curl, python3.
set -euo pipefail
PKG=com.leslie.tvhome
REPO=https://github.com/lesnolie/Tigris
MODE=install
FLAVOR=standard
UNINSTALL=0
APK_ONLY=0
AD_PACKAGES=()
AD_FINGERPRINT=
ACK_RECOVERY=0
ALLOW_FIRMWARE_CHANGE=0
while (($#)); do
  case "$1" in
    install|restore|inspect|verify|full-inspect|full-plan|full-apply-ads|full-restore-ads|full-home-inspect|full-home-probe|full-home-activate|full-home-restore) MODE=$1 ;;
    --ad-package) shift; AD_PACKAGES+=("${1:?Missing ad package}") ;;
    --confirm-fingerprint) shift; AD_FINGERPRINT=${1:?Missing fingerprint} ;;
    --acknowledge-recovery) ACK_RECOVERY=1 ;;
    --allow-firmware-change) ALLOW_FIRMWARE_CHANGE=1 ;;
    --uninstall) UNINSTALL=1 ;;
    --apk-only) APK_ONLY=1 ;;
    -s|--flavor) shift; FLAVOR=${1:?Missing standard or guard} ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done
fail() { echo "ERROR: $*" >&2; exit 1; }
[[ "$FLAVOR" == standard || "$FLAVOR" == guard ]] || fail 'Use -s standard or -s guard'
[[ "$MODE" == restore || "$UNINSTALL" == 0 ]] || fail '--uninstall is only for restore'
[[ "$MODE" == install || "$APK_ONLY" == 0 ]] || fail '--apk-only is only for install'
: "${TV:?Set TV to your TV IPv4 address, for example TV=192.168.1.20}"
[[ "$TV" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+(:[0-9]+)?$ ]] || fail 'TV must be an IPv4 address, optionally with :port'
SERIAL=$TV
[[ "$SERIAL" == *:* ]] || SERIAL="$SERIAL:5555"
for tool in adb python3; do command -v "$tool" >/dev/null || fail "Install $tool first"; done
ad() { adb -s "$SERIAL" "$@"; }
shread() { ad shell "$@" | tr -d '\r'; }
adb connect "$SERIAL"
[[ "$(ad get-state)" == device ]] || fail 'Enable network ADB, then allow debugging on the TV and retry'
SDK=$(shread getprop ro.build.version.sdk)
[[ "$SDK" =~ ^[0-9]+$ ]] && (( SDK >= 28 )) || fail 'Tigris requires Android 9 / API 28 or newer'
STATE_SERIAL=$SERIAL
case "${OSTYPE:-}" in msys*|cygwin*) STATE_SERIAL=${SERIAL//:/_} ;; esac
STATE=${STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/tigris/$STATE_SERIAL}
mkdir -p "$STATE"
chmod 700 "$STATE"
HOME_QUERY=(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
# Replacement engine is brand-isolated and not a generic package remover.
# The shared installer downloads its companion files for curl-piped invocations.
if [[ "$MODE" == full-* ]]; then
  REPLACEMENT="$(dirname "${BASH_SOURCE[0]:-.}")/tools/tv-replacement.py"
  if [[ ! -f "$REPLACEMENT" ]]; then
    command -v curl >/dev/null || fail 'Install curl first'
    REPLACEMENT="$TMP/tv-replacement.py"
    curl -fLsS https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/tv-replacement.py -o "$REPLACEMENT"
    curl -fLsS https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/replacement_profiles.py -o "$TMP/replacement_profiles.py"
    curl -fLsS https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/xiaomi_home.py -o "$TMP/xiaomi_home.py"
    curl -fLsS https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/installation_strategy.py -o "$TMP/installation_strategy.py"
    curl -fLsS https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/installation_strategies.json -o "$TMP/installation_strategies.json"
    curl -fLsS https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/vendor_install_guidance.py -o "$TMP/vendor_install_guidance.py"
    curl -fLsS https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/vendor_install_guidance.json -o "$TMP/vendor_install_guidance.json"
  fi
  case "$MODE" in
    full-inspect) R_MODE=inspect ;;
    full-plan) R_MODE=plan ;;
    full-apply-ads) R_MODE=apply-ads ;;
    full-restore-ads) R_MODE=restore-ads ;;
    full-home-inspect) R_MODE=home-inspect ;;
    full-home-probe) R_MODE=home-probe ;;
    full-home-activate) R_MODE=home-activate ;;
    full-home-restore) R_MODE=home-restore ;;
  esac
  R_ARGS=("$R_MODE" --serial "$SERIAL" --state-dir "$STATE")
  for p in "${AD_PACKAGES[@]}"; do R_ARGS+=(--package "$p"); done
  [[ -z "$AD_FINGERPRINT" ]] || R_ARGS+=(--confirm-fingerprint "$AD_FINGERPRINT")
  (( ACK_RECOVERY == 0 )) || R_ARGS+=(--acknowledge-recovery)
  (( ALLOW_FIRMWARE_CHANGE == 0 )) || R_ARGS+=(--allow-firmware-change)
  python3 "$REPLACEMENT" "${R_ARGS[@]}"
  exit $?
fi
# The same checker is used by manual users and AI agents. Piped installs fetch it.
CHECKER="$(dirname "${BASH_SOURCE[0]:-.}")/tools/check-install.py"
if [[ ! -f "$CHECKER" ]]; then
  command -v curl >/dev/null || fail 'Install curl first'
  CHECKER="$TMP/check-install.py"
  curl -fLsS https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/check-install.py -o "$CHECKER"
fi
verify() {
  python3 "$CHECKER" verify --serial "$SERIAL" --expected "${1:-$PKG/.HomeActivity}" --report "$STATE/verification.json"
}
if [[ "$MODE" == inspect ]]; then python3 "$CHECKER" inspect --serial "$SERIAL"; exit $?; fi
if [[ "$MODE" == verify ]]; then verify; exit $?; fi
if [[ "$MODE" == restore ]]; then
  [[ -f "$STATE/home" ]] || fail "No saved original launcher at $STATE. Follow AGENTS.md; do not guess the original launcher."
  ORIGINAL=$(cat "$STATE/home")
  [[ "$ORIGINAL" == */* && "$ORIGINAL" != *"$PKG"* && "$ORIGINAL" =~ ^[a-zA-Z0-9._/]+$ ]] || fail 'Saved original launcher is invalid; restore manually with AGENTS.md'
  ad shell am broadcast -a "$PKG.CONFIG" -n "$PKG/.ConfigReceiver" --ez guard false --ez keep_adb false
  CURRENT_SAVER=$(shread settings get secure screensaver_components)
  if [[ "$CURRENT_SAVER" == *"$PKG"* && -f "$STATE/screensaver" ]]; then
    SAVER=$(cat "$STATE/screensaver")
    if [[ "$SAVER" == null || -z "$SAVER" ]]; then ad shell settings delete secure screensaver_components
    else
      [[ "$SAVER" =~ ^[a-zA-Z0-9._/:]+$ ]] || fail 'Invalid saved screensaver'
      ad shell settings put secure screensaver_components "$SAVER"
    fi
  fi
  ad shell cmd package set-home-activity "$ORIGINAL"
  if [[ -f "$STATE/listeners" ]] && ! grep -q "$PKG" "$STATE/listeners"; then
    ad shell cmd notification disallow_listener "$PKG/$PKG.RevivalListener"
  fi
  ad shell am force-stop "$PKG"
  verify "$ORIGINAL" || fail 'Original HOME was not verified; Tigris has NOT been uninstalled. See verification.json.'
  if (( UNINSTALL )); then ad uninstall "$PKG"; fi
  echo 'Original HOME passed the ADB check. Test the physical remote separately. Tigris remains installed unless --uninstall was supplied.'
  exit 0
fi
KEEP_ADB=${KEEP_ADB:-preserve}
[[ "$KEEP_ADB" == preserve || "$KEEP_ADB" == 0 || "$KEEP_ADB" == 1 ]] || fail 'KEEP_ADB must be 0 or 1'
command -v curl >/dev/null || fail 'Install curl first'
# Never overwrite the first snapshot during an upgrade.
if [[ ! -f "$STATE/home" ]]; then
  shread "${HOME_QUERY[@]}" | tail -n 1 > "$STATE/home"
  shread settings get secure screensaver_components > "$STATE/screensaver"
  shread settings get secure enabled_accessibility_services > "$STATE/accessibility"
  shread settings get secure enabled_notification_listeners > "$STATE/listeners"
fi
# Select assets from the GitHub release metadata rather than assuming unversioned filenames.
# Works with versioned v2.0.6+ assets and historical Tigris.apk / Tigris-guard.apk releases.
ASSET=$( [[ "$FLAVOR" == guard ]] && echo Tigris-guard.apk || echo Tigris.apk )
if [[ -n "${APK:-}" ]]; then
  [[ -f "$APK" ]] || fail 'APK file not found'
  cp "$APK" "$TMP/$ASSET"
else
  VERSION=${VERSION:-latest}
  if [[ "$VERSION" == latest ]]; then
    METADATA=https://api.github.com/repos/lesnolie/Tigris/releases/latest
  else
    [[ "$VERSION" =~ ^v[0-9]+\.[0-9]+(\.[0-9]+)?(-[a-zA-Z0-9._-]+)?$ ]] || fail 'Invalid VERSION tag'
    METADATA="https://api.github.com/repos/lesnolie/Tigris/releases/tags/$VERSION"
  fi
  curl -fLsS "$METADATA" -o "$TMP/release.json"
  python3 - "$TMP/release.json" "$FLAVOR" "$TMP" "$VERSION" <<'PY'
import json, pathlib, re, sys

release_path, flavor, directory, requested = sys.argv[1:]
data = json.loads(pathlib.Path(release_path).read_text(encoding="utf-8"))
tag = data["tag_name"]
if not isinstance(tag, str) or not re.fullmatch(r"v[0-9]+\.[0-9]+(?:\.[0-9]+)?(?:-[A-Za-z0-9._-]+)?", tag):
    raise SystemExit("Invalid release tag")
if requested != "latest" and tag != requested:
    raise SystemExit("Release tag mismatch")
if data.get("draft") or data.get("prerelease"):
    raise SystemExit("Only stable releases are installable")
assets = {asset["name"]: asset["browser_download_url"] for asset in data["assets"]}
base = "Tigris-guard" if flavor == "guard" else "Tigris"
preferred_apk = (f"{base}-{tag}.apk", f"{base}.apk")
preferred_checksums = (f"SHA256SUMS-{tag}.txt", "SHA256SUMS.txt")
def select(candidates):
    for name in candidates:
        if name in assets:
            expected = f"https://github.com/lesnolie/Tigris/releases/download/{tag}/{name}"
            if assets[name] != expected:
                raise SystemExit("Unexpected release asset URL")
            return name, expected
    raise SystemExit("Required APK or SHA256SUMS file missing from release")
apk, url = select(preferred_apk)
checksum, checksum_url = select(preferred_checksums)
out = pathlib.Path(directory)
(out / "asset_name").write_text(apk, encoding="utf-8")
(out / "asset_url").write_text(url, encoding="utf-8")
(out / "checksum_url").write_text(checksum_url, encoding="utf-8")
PY
  ASSET=$(cat "$TMP/asset_name")
  ASSET_URL=$(cat "$TMP/asset_url")
  CHECKSUM_URL=$(cat "$TMP/checksum_url")
  curl -fLsS "$ASSET_URL" -o "$TMP/$ASSET"
  curl -fLsS "$CHECKSUM_URL" -o "$TMP/SHA256SUMS.txt"
  python3 - "$TMP/$ASSET" "$TMP/SHA256SUMS.txt" "$ASSET" <<'PY'
import hashlib, pathlib, sys
apk, manifest, name = sys.argv[1:]
expected = [line.split()[0].lower() for line in pathlib.Path(manifest).read_text().splitlines()
            if len(line.split()) == 2 and line.split()[1].lstrip('*') == name]
actual = hashlib.sha256(pathlib.Path(apk).read_bytes()).hexdigest()
if expected != [actual]: raise SystemExit('SHA256 check failed; installation stopped')
PY
fi
if ! ad install -r "$TMP/$ASSET"; then
  fail 'Installation failed; nothing uninstalled. Signature mismatch requires the original signing key. TCL verification failure: use Guard APK via tools/tcast-push.py (see AGENTS.md).'
fi
if (( APK_ONLY )); then
  echo "APK installed/updated without HOME or Guard changes. Original state: $STATE"
  echo 'HOME, physical remote, wake, boot and ads: UNVERIFIED. Run tools/install-plan.py again for diagnostics.'
  exit 0
fi
GUARD=false
if [[ "$FLAVOR" == standard ]]; then
  ad shell cmd package set-home-activity "$PKG/.HomeActivity" || true
  # A resolver or command saying Success is not evidence that HOME actually works.
  if verify; then :; else
    CHECK_STATUS=$?
    if [[ "$CHECK_STATUS" == 1 ]]; then GUARD=true
    else fail 'APK installed, but HOME is unverified. Complete any TV prompts and retry verify; no guard workaround applied.'
    fi
  fi
else GUARD=true
fi
if [[ "$GUARD" == true || "$KEEP_ADB" == 1 ]]; then
  ad shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS
fi
if [[ "$GUARD" == true ]]; then
  ad shell cmd notification allow_listener "$PKG/$PKG.RevivalListener"
  ad shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
  BRAND="$(shread getprop ro.product.manufacturer) $(shread getprop ro.product.brand)"
  BRAND=$(echo "$BRAND" | tr '[:upper:]' '[:lower:]')
  if [[ "$BRAND" == *tcl* || "$BRAND" == *ffalcon* ]]; then
    ad shell appops set "$PKG" APP_AUTO_START allow || true
    ad shell appops set "$PKG" APP_ASSOC_START allow || true
  fi
fi
# An upgrade never turns off a guard the user previously enabled.
CONFIG=()
[[ "$KEEP_ADB" == preserve ]] || CONFIG+=(--ez keep_adb "$([[ "$KEEP_ADB" == 1 ]] && echo true || echo false)")
[[ "$GUARD" == true ]] && CONFIG+=(--ez guard true)
if [[ "$GUARD" == true || "$KEEP_ADB" != preserve ]]; then
  ad shell am broadcast -a "$PKG.CONFIG" -n "$PKG/.ConfigReceiver" "${CONFIG[@]}"
fi
if [[ "$GUARD" == true ]]; then
  ad shell am start -n "$PKG/.HomeActivity"
  sleep 3
  verify || fail 'APK installed, but HOME failed or is unverified. See verification.json and docs/installation-compatibility.md. Keep the stock launcher enabled.'
fi
echo "Original launcher snapshot: $STATE (keep this directory for restore)."
echo 'APK installed; ADB HOME passed. Physical remote HOME: UNVERIFIED. Standby/wake: UNVERIFIED. Reboot: UNVERIFIED. See verification.json.'
