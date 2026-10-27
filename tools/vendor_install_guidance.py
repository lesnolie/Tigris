"""Research-only vendor guidance for Tigris host installation.

This registry describes what to INSPECT, not what to execute on a TV. It is
intentionally separate from installation_strategies.json: a published finding
from another project can never authorize Tigris to mutate firmware.
"""
import json
from pathlib import Path


def registry():
    data = json.loads(Path(__file__).with_name("vendor_install_guidance.json").read_text(encoding="utf-8"))
    if data.get("schema") != 1:
        raise ValueError("Unsupported vendor guidance schema")
    rows = data["records"]
    ids = [row["id"] for row in rows]
    if len(ids) != len(set(ids)) or ids.count("generic") != 1:
        raise ValueError("Duplicate or missing generic vendor guidance")
    found = set()
    for row in rows:
        if not all(row.get(k) for k in ("id", "path", "mechanisms", "inspect", "fallback", "upstream")):
            raise ValueError("Incomplete vendor guidance")
        if not isinstance(row.get("brands"), list):
            raise ValueError("Invalid brand matcher")
        for brand in row["brands"]:
            value = brand.casefold().strip()
            if not value or value in found:
                raise ValueError("Ambiguous vendor brand: " + brand)
            found.add(value)
    return rows


def guidance_for(device):
    """Return explanatory hints; NEVER grant write privileges or override an installer."""
    brand = {str(device.get(k) or "").strip().casefold()
             for k in ("manufacturer", "brand")}
    brand.discard("")
    choices = []
    rows = registry()
    for row in rows:
        if row["brands"] and brand.intersection(row["brands"]):
            choices.append(row)
    if len(choices) > 1:
        return {"status": "identity_conflict", "paths": sorted(row["path"] for row in choices),
                "read_only": True, "hardware_verified": False, "mutations": []}
    chosen = choices[0] if choices else next(row for row in rows if row["id"] == "generic")
    # Diagnostic signals are based on sample reports, not proof the current TV
    # has the same firmware policy. Do not implicitly execute vendor commands.
    return {
        "status": "research_guidance",
        "vendor": chosen["id"],
        "route": chosen["path"],
        "mechanisms_to_check": chosen["mechanisms"],
        "inspect_before_any_change": chosen["inspect"],
        "safe_fallback": chosen["fallback"],
        "sources": chosen["upstream"],
        "read_only": True,
        "hardware_verified": False,
        "mutations": [],
        "warning": "Other projects' case studies are not Tigris device validation. "
                   "Match exact firmware and record the original HOME before any opt-in trial.",
    }
