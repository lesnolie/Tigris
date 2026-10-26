#!/usr/bin/env python3
"""Fail publication if an APK cannot preserve the previous official upgrade identity.

This inspects APKs with Android build-tools, never installs or touches a TV.
Device-specific installation restrictions still require hardware testing.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess

STABLE_CERT = "7d2b6be8a86ce8981e368dde4920869b90c8206137934a6d1fc89a2d1d1b6114"


def inspect(apk, tools):
    def run(name, *args):
        executable = tools / name
        if not executable.exists():
            executable = tools / (name + (".bat" if name == "apksigner" else ".exe"))
        result = subprocess.run([str(executable), *map(str, args)], check=True,
                                capture_output=True, text=True, encoding="utf-8", timeout=120)
        return result.stdout

    badging = run("aapt", "dump", "badging", apk)
    identity = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    sdk = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    target = re.search(r"^targetSdkVersion:'(\d+)'", badging, re.M)
    if not identity or not sdk or not target:
        raise ValueError("Missing APK identity or SDK requirements")
    native = re.search(r"^native-code:(.*)$", badging, re.M)
    # apksigner checks cryptographic validity as well as printing the certificate.
    signature = run("apksigner", "verify", "--min-sdk-version", "28", "--print-certs", apk)
    signers = set(re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})", signature))
    manifest = run("aapt", "dump", "xmltree", apk, "AndroidManifest.xml")
    return {"package": identity[1], "code": int(identity[2]), "version": identity[3],
            "min_sdk": int(sdk[1]), "target_sdk": int(target[1]),
            "abis": set(re.findall(r"'([^']+)'", native[1])) if native else set(),
            "signers": {s.lower() for s in signers},
            "home": '"android.intent.category.HOME"' in manifest}


def verify(old, new, version, flavor):
    if old["package"] != "com.leslie.tvhome" or new["package"] != old["package"]:
        raise ValueError("Application ID changed")
    if old["signers"] != {STABLE_CERT} or new["signers"] != old["signers"]:
        raise ValueError("Official signing certificate changed or missing")
    if new["code"] <= old["code"] or new["version"] != version:
        raise ValueError("Version must match the tag and increase versionCode")
    if new["min_sdk"] > old["min_sdk"] or new["target_sdk"] != old["target_sdk"]:
        raise ValueError("Android requirements or target behavior changed; review before release")
    if new["abis"] and (not old["abis"] or not old["abis"] <= new["abis"]):
        raise ValueError("CPU architecture support narrowed")
    if flavor not in ("standard", "guard") or old["home"] != new["home"] or new["home"] != (flavor == "standard"):
        raise ValueError("Standard/Guard HOME declaration changed")
    return {"result": "upgrade_identity_verified", "flavor": flavor,
            "from": old["version"], "to": new["version"], "version_code": new["code"],
            "signer": STABLE_CERT, "physical_install": "unverified"}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("old", type=Path)
    p.add_argument("new", type=Path)
    p.add_argument("--tools", required=True, type=Path)
    p.add_argument("--version", required=True)
    p.add_argument("--flavor", required=True, choices=("standard", "guard"))
    args = p.parse_args()
    result = verify(inspect(args.old, args.tools), inspect(args.new, args.tools), args.version, args.flavor)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
