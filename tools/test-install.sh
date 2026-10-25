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
  'shell cmd package query-activities'*) echo com.vendor.home/.Home ;;
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
if [[ "$1" == *check-install.py ]]; then
  exec python -c 'import os,runpy,sys,time,subprocess; time.sleep=lambda _:None; original=subprocess.run; subprocess.run=lambda args,**kw:original([os.environ["MOCK_BASH"],os.environ["MOCK_ADB"],*args[1:]],**kw); sys.argv=sys.argv[1:]; runpy.run_path(sys.argv[0],run_name="__main__")' "$@"
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
! grep -q -- '--ez guard true' "$LOG"
echo 'PASS Xiaomi with working HOME does not enable guard by brand'
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
