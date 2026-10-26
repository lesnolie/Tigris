#!/usr/bin/env python3
"""Development checkout installer: plan by default, --execute explicitly installs.

PowerShell: python tools/install-plan.py --platform android --serial 192.168.1.20:5555
POSIX: python3 tools/install-plan.py --platform android --serial 192.168.1.20:5555
Uses existing install.sh operations (Bash required for execution, including Windows).
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

from installation_strategy import select

ROOT = Path(__file__).resolve().parent.parent


def execute(plan, serial, runner=subprocess.run):
    if plan["status"] != "planned":
        raise ValueError("No executable strategy")
    # install.sh currently supports network IPv4 only; do not pass arbitrary shell text.
    import re
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){3}(?::[0-9]+)?", serial):
        raise ValueError("Execution requires a network IPv4 ADB serial")
    strategy = plan["strategy"]
    if strategy["executor"] not in ("legacy", "apk-only"):
        raise ValueError("Unknown executor; no commands run")
    args = ["bash", str(ROOT / "install.sh"), "install", "--flavor", strategy["apk"]]
    if strategy["executor"] == "apk-only":
        args += ["--apk-only"]
    env = dict(os.environ, TV=serial)
    if os.name == "nt" and not env.get("STATE_DIR"):
        # ':' is valid in an ADB serial, but not in a Windows directory name.
        env["STATE_DIR"] = (Path.home() / ".local/state/tigris" / serial.replace(":", "_")).as_posix()
    return runner(args, env=env, cwd=ROOT, check=False).returncode


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--platform", required=True, choices=("android", "webos", "tizen", "unknown"))
    p.add_argument("--serial")
    p.add_argument("--execute", action="store_true")
    p.add_argument("--flavor", choices=("standard", "guard"),
                   help="Explicit existing TCL Guard path; never selected just by brand")
    args = p.parse_args()
    if args.platform != "android":
        print(json.dumps(select({}, args.platform)))
        return 2  # Before even invoking ADB.
    if not args.serial:
        p.error("--serial is required for Android")
    spec = importlib.util.spec_from_file_location("replacement", ROOT / "tools/tv-replacement.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    try:
        connection = subprocess.run(["adb", "connect", args.serial], capture_output=True, text=True, timeout=25)
        state = subprocess.run(["adb", "-s", args.serial, "get-state"], capture_output=True, text=True, timeout=25)
        if connection.returncode or state.returncode or state.stdout.strip() != "device":
            raise ValueError("ADB disconnected or unauthorized; allow debugging on the TV")
        inspection = module.Tv(args.serial).inspect()
        plan = select(inspection["device"])
        if args.flavor:
            if plan.get("strategy", {}).get("id") != "tcl-compatible":
                raise ValueError("Flavor override is only for the existing TCL/FFalcon path")
            plan["strategy"]["apk"] = args.flavor
            plan["flavor_source"] = "explicit_user_selection"
        plan["home_route"] = inspection["home_route"]
        plan["adaptive_install"] = module.adaptive_install_decision(inspection)
        print(json.dumps(plan, ensure_ascii=False, indent=2), flush=True)
        if args.execute:
            return execute(plan, args.serial)
        return 0 if plan["status"] == "planned" else 2
    except (OSError, ValueError, module.Failure, subprocess.TimeoutExpired) as error:
        print(json.dumps({"status": "blocked", "reason": str(error)}))
        return 2


if __name__ == "__main__":
    sys.exit(main())
