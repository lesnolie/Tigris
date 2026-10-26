"""Shared, deterministic installation plans. No device writes or runtime imports."""
import json
from pathlib import Path


def registry():
    data = json.loads(Path(__file__).with_name("installation_strategies.json").read_text(encoding="utf-8"))
    if data["schema"] != 1:
        raise ValueError("Unsupported strategy schema")
    rows = data["strategies"]
    if len({s["id"] for s in rows}) != len(rows):
        raise ValueError("Duplicate strategy ID")
    return rows


def select(device, platform="android", strategies=None):
    """Equal-priority conflicts block execution, never pick a riskier first match."""
    if platform != "android":
        return {"status": "blocked", "reason": "separate_platform_installer_required"}
    sdk = str(device.get("sdk", ""))
    if not sdk.isdigit() or int(sdk) < 28:
        return {"status": "blocked", "reason": "android_9_required"}
    if not all(device.get(k) for k in ("manufacturer", "brand", "model", "fingerprint")):
        return {"status": "diagnostic_only", "reason": "incomplete_identity"}
    brands = {device[k].strip().lower() for k in ("manufacturer", "brand")}
    matches = [s for s in (registry() if strategies is None else strategies)
               if s["platform"] == platform and int(sdk) >= s["min_sdk"]
               and (not s["brands"] or brands.intersection(s["brands"]))
               and (not s["fingerprints"] or device["fingerprint"] in s["fingerprints"])]
    if not matches:
        return {"status": "diagnostic_only", "reason": "no_match"}
    best = max(s["priority"] for s in matches)
    winners = [s for s in matches if s["priority"] == best]
    if len(winners) != 1:
        return {"status": "diagnostic_only", "reason": "conflicting_rules",
                "matches": sorted(s["id"] for s in winners)}
    return {"status": "planned", "strategy": winners[0], "identity": device,
            "evidence_level": "observed_identity_inferred_strategy",
            "hardware_verified": False, "automatic_package_changes": [],
            "results": {key: "unverified" for key in winners[0]["verification"]}}
