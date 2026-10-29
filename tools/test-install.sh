#!/usr/bin/env bash
# Isolated installer contract tests. Never reaches a real adb executable.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TEST=$(mktemp -d)
trap 'rm -rf "$TEST"' EXIT
mkdir "$TEST/bin"
export LOG="$TEST/commands"
cat > "$TEST/bin/adb" <<'MOCK'
#!/usr/bin/env bash
echo "$*" >> "$LOG"
[[ "$1" == -s ]] && shift 2
case "$*" in
  connect*) echo connected ;;
  get-state) echo device ;;
  'shell getprop ro.build.version.sdk') echo "${SDK:-31}" ;;
  'shell getprop ro.product.manufacturer') echo "${BRAND:-Sony}" ;;
  'shell getprop ro.product.brand') echo "${BRAND:-Sony}" ;;
  'shell getprop ro.product.model') echo "${MODEL:-TestTV}" ;;
  'shell getprop ro.build.version.incremental') echo 2026-test ;;
  'shell getprop ro.build.fingerprint') echo "test/device/fingerprint" ;;
  'shell pm list packages') printf 'package:com.leslie.tvhome\npackage:com.mitv.tvhome\npackage:com.android.tv.settings\npackage:com.miui.systemAdSolution\n' ;;
  'shell pm list packages -d') : ;;
  'shell cmd package query-activities'*)
    if [[ "${STOCK_PRIORITY:-0}" != 0 ]]; then
      echo "ResolveInfo{com.vendor.home/.Home p=${STOCK_PRIORITY} o=0 m=0x108000}"
    else
      echo com.vendor.home/.Home
    fi ;;
  'shell dumpsys activity activities')
    raw=$(cat "$LOG.focus" 2>/dev/null || true)
    comp=${raw#mCurrentFocus=}
    if [[ -z "$comp" || "$comp" == "$raw" ]]; then comp="${FOCUS_APP:-com.leslie.tvhome/.HomeActivity}"; fi
    echo "mFocusedApp=ActivityRecord{u0 ${comp} t1}" ;;
  'shell dumpsys package com.android.tv.settings') echo 'FallbackHome' ;;
  'shell cmd package resolve-activity'*) echo "${RESOLVED_HOME:-com.vendor.home/.Home}" ;;
  'shell settings get secure screensaver_components') echo "${SAVER:-com.vendor.saver/.Dream}" ;;
  'shell settings get secure enabled_notification_listeners') echo com.other/.Listener ;;
  'shell settings get secure enabled_accessibility_services') echo com.other/.Service ;;
  'shell pm path com.leslie.tvhome') echo package:/data/app/tigris/base.apk ;;
  'shell am start -W -a android.settings.SETTINGS')
    [[ "${START_FAIL:-0}" == 0 ]] || { echo 'Error: Activity not found'; exit 0; }
    echo 'mCurrentFocus=com.android.settings/.Settings' > "$LOG.focus"; echo 'Status: ok' ;;
  'shell cmd package set-home-activity '*) echo "${*: -1}" > "$LOG.home"; echo Success ;;
  'shell input keyevent KEYCODE_HOME')
    target=$(cat "$LOG.home" 2>/dev/null || echo com.leslie.tvhome/.HomeActivity)
    [[ "${HOME_FAIL:-0}" == 0 ]] || target=com.vendor.home/.Home
    if [[ "${NEED_GUARD:-0}" == 1 && ! -f "$LOG.guard" ]]; then target=com.vendor.home/.Home; fi
    echo "mCurrentFocus=$target" > "$LOG.focus" ;;
  'shell am broadcast'*'--ez guard true'*) touch "$LOG.guard" ;;
  install*) [[ "${FAIL_INSTALL:-0}" == 0 ]] || { echo INSTALL_FAILED_UPDATE_INCOMPATIBLE >&2; exit 1; }; echo com.leslie.tvhome/.HomeActivity > "$LOG.home"; rm -f "$LOG.guard"; echo Success ;;
  'shell dumpsys window') cat "$LOG.focus" ;;
esac
MOCK
printf '#!/usr/bin/env bash\nexit 0\n' > "$TEST/bin/sleep"
chmod +x "$TEST/bin/adb" "$TEST/bin/sleep"
# Git Bash may see the Windows Store python3 alias rather than installed Python.
if ! python3 -c 'import sys' >/dev/null 2>&1; then
  printf '#!/usr/bin/env bash\nexec python "$@"\n' > "$TEST/bin/python3"
  chmod +x "$TEST/bin/python3"
fi
# Run the real evidence checker with a virtual clock so polling tests are fast.
cat > "$TEST/bin/python3" <<'MOCK'
#!/usr/bin/env bash
if [[ "$1" == *.py ]]; then
  exec python -c 'import os,runpy,sys,time,subprocess; time.sleep=lambda _:None; original=subprocess.run; subprocess.run=lambda args,**kw:original([os.environ["MOCK_BASH"],os.environ["MOCK_ADB"],*args[1:]],**kw) if args[0]=="adb" else (_ for _ in ()).throw(RuntimeError("Unexpected subprocess in isolated test")); sys.argv=sys.argv[1:]; sys.path.insert(0,os.path.dirname(os.path.abspath(sys.argv[0]))); runpy.run_path(sys.argv[0],run_name="__main__")' "$@"
fi
exec python "$@"
MOCK
chmod +x "$TEST/bin/python3"
export MOCK_BASH=$(command -v bash) MOCK_ADB="$TEST/bin/adb"
if command -v cygpath >/dev/null; then
  MOCK_BASH=$(cygpath -w "$MOCK_BASH")
  MOCK_ADB=$(cygpath -w "$MOCK_ADB")
fi
export PATH="$TEST/bin:$PATH" TV=192.168.1.20 APK="$TEST/local.apk" STATE_DIR="$TEST/state"
printf fake-apk > "$APK"
bash "$ROOT/install.sh" > "$TEST/output"
[[ "$(cat "$STATE_DIR/home")" == com.vendor.home/.Home ]]
grep -q 'install -r' "$LOG"
! grep -Eq 'uninstall|disable-user|keyevent KEYCODE_POWER' "$LOG"
echo 'PASS install snapshots original launcher and never removes apps'
: > "$LOG"
BRAND=Xiaomi bash "$ROOT/install.sh" --apk-only > "$TEST/output"
grep -q 'install -r' "$LOG"
! grep -Eq 'set-home-activity|am broadcast|pm grant|appops set|keyevent|disable-user|uninstall' "$LOG"
echo 'PASS safe APK-only route preserves HOME, Guard and permissions'
: > "$LOG"
BRAND=TCL bash "$ROOT/install.sh" --flavor guard > "$TEST/output"
grep -q 'APP_AUTO_START allow' "$LOG"
grep -q 'APP_ASSOC_START allow' "$LOG"
grep -q -- '--ez guard true' "$LOG"
! grep -Eq 'set-home-activity|disable-user|uninstall' "$LOG"
echo 'PASS explicit TCL Guard retains vendor autostart and original HOME'
printf com.saved.home/.Home > "$STATE_DIR/home"
bash "$ROOT/install.sh" > "$TEST/output"
[[ "$(cat "$STATE_DIR/home")" == com.saved.home/.Home ]]
echo 'PASS upgrade retains initial snapshot'
! grep -q -- '--ez keep_adb' "$LOG"
echo 'PASS omitted keep_adb preserves user preference'
: > "$LOG"
KEEP_ADB=1 bash "$ROOT/install.sh" > "$TEST/output"
grep -q -- '--ez keep_adb true' "$LOG"
: > "$LOG"
KEEP_ADB=0 bash "$ROOT/install.sh" > "$TEST/output"
grep -q -- '--ez keep_adb false' "$LOG"
echo 'PASS explicit keep_adb changes still apply'
: > "$LOG"
RESOLVED_HOME=com.leslie.tvhome/.HomeActivity bash "$ROOT/install.sh" > "$TEST/output"
! grep -q 'am broadcast' "$LOG"
echo 'PASS native HOME upgrade with no configuration skips empty broadcast'
: > "$LOG"
BRAND=Xiaomi bash "$ROOT/install.sh" > "$TEST/output"
grep -q -- '--ez guard true' "$LOG"
grep -q 'GET_USAGE_STATS allow' "$LOG"
grep -q 'ACCESS_RESTRICTED_SETTINGS allow' "$LOG"
grep -q 'WRITE_SETTINGS allow' "$LOG"
grep -q 'role add-role-holder' "$LOG"
role_line=$(grep -n 'role add-role-holder' "$LOG" | head -1 | cut -d: -f1)
home_line=$(grep -n 'set-home-activity' "$LOG" | head -1 | cut -d: -f1)
[[ "$role_line" -lt "$home_line" ]]
echo 'PASS Xiaomi keeps the guard on and holds the HOME role before set-home-activity'
: > "$LOG"
NEED_GUARD=1 RESOLVED_HOME=com.leslie.tvhome/.HomeActivity bash "$ROOT/install.sh" > "$TEST/output"
grep -q -- '--ez guard true' "$LOG"
echo 'PASS actual HOME failure enables guard even when resolver reports Tigris'
: > "$LOG"
if HOME_FAIL=1 bash "$ROOT/install.sh" > "$TEST/output" 2>&1; then exit 1; fi
grep -q 'APK installed, but HOME failed or is unverified' "$TEST/output"
echo 'PASS unresolved HOME failure is not a successful installation'
: > "$LOG"
if START_FAIL=1 bash "$ROOT/install.sh" > "$TEST/output" 2>&1; then exit 1; fi
! grep -q -- '--ez guard true' "$LOG"
echo 'PASS missing verification baseline does not enable guard'
: > "$LOG"
rm -f "$LOG.focus"
STOCK_PRIORITY=1 BRAND=Sony bash "$ROOT/install.sh" > "$TEST/output"
grep -q -- '--ez guard true' "$LOG"
echo 'PASS Sony with a stock launcher priority above zero enables the guard'
: > "$LOG"
STOCK_PRIORITY=0 BRAND=Google bash "$ROOT/install.sh" > "$TEST/output"
! grep -q -- '--ez guard true' "$LOG"
echo 'PASS Google TV with no stock launcher priority leaves the guard off'
: > "$LOG"
BRAND=Hisense bash "$ROOT/install.sh" > "$TEST/output"
grep -q -- '--ez guard true' "$LOG"
echo 'PASS Hisense enables the guard'
: > "$LOG"
BRAND=Skyworth bash "$ROOT/install.sh" > "$TEST/output"
grep -q -- '--ez guard true' "$LOG"
echo 'PASS Coocaa/Skyworth enables the guard'
: > "$LOG"
rm -f "$LOG.focus"
bash "$ROOT/install.sh" repair > "$TEST/output"
grep -q 'GET_USAGE_STATS allow' "$LOG"
grep -q -- '--ez guard true' "$LOG"
grep -q 'mFocusedApp:' "$TEST/output"
! grep -Eq 'disable-user|uninstall|set-home-activity' "$LOG"
echo 'PASS repair re-grants the guard and reads mFocusedApp without disabling the stock launcher'
: > "$LOG"
bash "$ROOT/install.sh" inspect > "$TEST/output"
! grep -Eq 'install -r|pm grant|am start|am broadcast|input keyevent|set-home-activity' "$LOG"
echo 'PASS inspect performs no TV mutations or key events'
: > "$LOG"
bash "$ROOT/install.sh" full-plan > "$TEST/output"
grep -q '"profile": "sony"' "$TEST/output"
grep -q '"commands_are_not_executed": true' "$TEST/output"
! grep -Eq 'install -r|pm disable-user|pm uninstall|pm enable|set-home-activity|keyevent' "$LOG"
echo 'PASS full-plan is read-only for non-Xiaomi brands'
: > "$LOG"
BRAND=Xiaomi bash "$ROOT/install.sh" full-plan > "$TEST/output"
grep -q '"profile": "xiaomi"' "$TEST/output"
grep -q '"home_allowlist": "unverified"' "$TEST/output"
! grep -Eq 'pm disable-user|pm uninstall|pm enable|set-home-activity|keyevent' "$LOG"
echo 'PASS Xiaomi full-plan inspects without mutating HOME'
: > "$LOG"
BRAND=Xiaomi bash "$ROOT/install.sh" full-home-inspect > "$TEST/output"
grep -q '"classification": "tigris_home_not_visible"' "$TEST/output"
grep -q '"read_only": true' "$TEST/output"
! grep -Eq 'pm disable-user|pm uninstall|pm enable|set-home-activity|keyevent' "$LOG"
echo 'PASS native HOME inspection is Xiaomi-scoped and read-only'
: > "$LOG"
if BRAND=Sony bash "$ROOT/install.sh" full-home-probe \
    --confirm-fingerprint test/device/fingerprint \
    --acknowledge-recovery > "$TEST/output" 2>&1; then exit 1; fi
! grep -Eq 'pm disable-user|pm uninstall|pm enable|set-home-activity' "$LOG"
echo 'PASS other brands cannot trigger Xiaomi native HOME trial'
: > "$LOG"
if BRAND=Sony bash "$ROOT/install.sh" full-apply-ads \
    --ad-package com.miui.systemAdSolution \
    --confirm-fingerprint test/device/fingerprint \
    --acknowledge-recovery > "$TEST/output" 2>&1; then exit 1; fi
! grep -Eq 'pm disable-user|pm uninstall|pm enable|set-home-activity' "$LOG"
echo 'PASS Xiaomi ad changes cannot run on Sony'
: > "$LOG"
if HOME_FAIL=1 bash "$ROOT/install.sh" restore --uninstall > "$TEST/output" 2>&1; then exit 1; fi
! grep -q '^.* uninstall ' "$LOG"
echo 'PASS restore failure blocks uninstall'
: > "$LOG"
if FAIL_INSTALL=1 bash "$ROOT/install.sh" > "$TEST/output" 2>&1; then exit 1; fi
! grep -Eq 'uninstall|pm grant|set-home-activity|am broadcast' "$LOG"
echo 'PASS signature mismatch stops before configuration without uninstall'
: > "$LOG"
SAVER=com.leslie.tvhome/.PhotoFrame bash "$ROOT/install.sh" restore > "$TEST/output"
grep -q 'set-home-activity com.saved.home/.Home' "$LOG"
grep -q 'settings put secure screensaver_components com.vendor.saver/.Dream' "$LOG"
! grep -Eq 'uninstall|settings put secure enabled_accessibility_services' "$LOG"
echo 'PASS restore retains app and other accessibility services'
: > "$LOG"
bash "$ROOT/install.sh" restore --uninstall > "$TEST/output"
grep -q 'uninstall com.leslie.tvhome' "$LOG"
echo 'PASS explicit uninstall supported'
: > "$LOG"
if STATE_DIR="$TEST/missing" bash "$ROOT/install.sh" restore > "$TEST/output" 2>&1; then exit 1; fi
! grep -Eq 'am broadcast|set-home-activity|uninstall' "$LOG"
echo 'PASS missing snapshot stops without changing TV'
: > "$LOG"
if SDK=26 bash "$ROOT/install.sh" > "$TEST/output" 2>&1; then exit 1; fi
! grep -q 'install -r' "$LOG"
echo 'PASS unsupported Android blocked'
# Simulate the public curl-piped installer (no local tools directory).
# All three Python modules must be downloaded before HOME inspection works.
mkdir -p "$TEST/piped"
cp "$ROOT/install.sh" "$TEST/piped/install.sh"
cat > "$TEST/bin/curl" <<'REMOTE_CURL'
#!/usr/bin/env bash
url="$2"
out="$4"
case "$url" in
  https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/*.py|https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/installation_strategies.json|https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/vendor_install_guidance.json)
    file="${url##*/}"
    cp "$SOURCE_ROOT/tools/$file" "$out"
    echo "$file" >> "$LOG.downloads"
    ;;
  *) echo "unexpected test URL: $url" >&2; exit 4 ;;
esac
REMOTE_CURL
chmod +x "$TEST/bin/curl"
: > "$LOG"
SOURCE_ROOT="$ROOT" BRAND=Xiaomi bash "$TEST/piped/install.sh" full-home-inspect > "$TEST/output"
grep -q '"read_only": true' "$TEST/output"
grep -Fxq 'tv-replacement.py' "$LOG.downloads"
grep -Fxq 'replacement_profiles.py' "$LOG.downloads"
grep -Fxq 'xiaomi_home.py' "$LOG.downloads"
grep -Fxq 'vendor_install_guidance.py' "$LOG.downloads"
grep -Fxq 'vendor_install_guidance.json' "$LOG.downloads"
! grep -Eq 'pm disable-user|pm uninstall|pm enable|set-home-activity|keyevent' "$LOG"
echo 'PASS piped installer fetches all HOME dependencies without writes'
cat > "$TEST/bin/curl" <<'MOCK'
#!/usr/bin/env bash
url=$2
out=$4
tag=${MOCK_RELEASE_TAG:-v2.0.6}
if [[ "${MOCK_LEGACY:-0}" == 1 ]]; then
  standard=Tigris.apk
  guard=Tigris-guard.apk
  checksums=SHA256SUMS.txt
else
  standard="Tigris-$tag.apk"
  guard="Tigris-guard-$tag.apk"
  checksums="SHA256SUMS-$tag.txt"
fi
base="https://github.com/lesnolie/Tigris/releases/download/$tag"
if [[ "$url" == *"api.github.com"* ]]; then
  printf '{"tag_name":"%s","draft":false,"prerelease":false,"assets":[{"name":"%s","browser_download_url":"%s/%s"},{"name":"%s","browser_download_url":"%s/%s"},{"name":"%s","browser_download_url":"%s/%s"}]}' \
    "$tag" "$standard" "$base" "$standard" "$guard" "$base" "$guard" "$checksums" "$base" "$checksums" > "$out"
elif [[ "$url" == *SHA256SUMS* ]]; then
  hash=$(python3 -c 'import hashlib; print(hashlib.sha256(b"downloaded-apk").hexdigest())')
  [[ "${BAD_HASH:-0}" == 0 ]] || hash=0000
  printf '%s  %s\n%s  %s\n' "$hash" "$standard" "$hash" "$guard" > "$out"
else
  printf downloaded-apk > "$out"
fi
MOCK
chmod +x "$TEST/bin/curl"
: > "$LOG"
APK='' bash "$ROOT/install.sh" > "$TEST/output"
grep -q 'Tigris-v2.0.6.apk' "$LOG"
echo 'PASS versioned standard release selected and checksum verified'
: > "$LOG"
APK='' bash "$ROOT/install.sh" -s guard > "$TEST/output"
grep -q 'Tigris-guard-v2.0.6.apk' "$LOG"
echo 'PASS versioned guard release selected and checksum verified'
: > "$LOG"
MOCK_LEGACY=1 MOCK_RELEASE_TAG=v2.0.5 VERSION=v2.0.5 APK='' bash "$ROOT/install.sh" > "$TEST/output"
grep -q 'Tigris.apk' "$LOG"
echo 'PASS legacy release names still install from exact version tag'
: > "$LOG"
if BAD_HASH=1 APK='' bash "$ROOT/install.sh" > "$TEST/output" 2>&1; then exit 1; fi
! grep -q 'install -r' "$LOG"
echo 'PASS checksum mismatch blocks installation'
