"""Brand-isolated policy for Tigris full-replacement installations.

These candidates are NOT a claim of safety on all firmwares. Xiaomi's verified
firmware profiles are intentionally empty until a physical TV has passed
Home/standby/reboot/restore testing. Never generalize a tv-fresh device finding.
"""
from dataclasses import dataclass

# Exact fingerprint -> reviewed packages. External model reports are insufficient.
# Intentionally empty: no firmware has supplied component-purpose + recovery evidence.
VERIFIED_AD_FIRMWARES: dict[str, tuple[str, ...]] = {}

@dataclass(frozen=True)
class Profile:
    name: str
    ad_candidates: tuple[str, ...] = ()
    note: str = ""
    full_home_verified: bool = False

XIAOMI_ADS = (
    "com.miui.systemAdSolution",
    "com.duokan.videodaily",
    "com.xiaomi.mitv.tvpush",
    "com.xiaomi.mitv.advertise",
    "com.mitv.tvad",
    "com.mitv.analysis",
    "com.miui.analytics",
    "com.miui.tv.analytics",
    "com.xiaomi.mistat.analyticstracker",
)
# Deliberately no stock launcher, system settings, TV player, or updater in ads.
CORE_DENY = frozenset({
    "com.mitv.tvhome", "com.android.tv.settings", "com.xiaomi.mitv.settings",
    "com.xiaomi.mitv.systemui", "com.mitv.tvplayer",
    "com.android.packageinstaller", "com.xiaomi.voicecontrol",
    "com.leslie.tvhome",
})

def detect(manufacturer: str, brand: str) -> Profile:
    values = {manufacturer.strip().lower(), brand.strip().lower()}
    if values.intersection({"xiaomi", "redmi"}):
        return Profile("xiaomi", XIAOMI_ADS,
                       "MIUI/HyperOS package names vary by firmware. No global HOME-system-package removal.")
    if values.intersection({"tcl", "ffalcon", "iffalcon"}):
        return Profile("tcl", note="TCL installer restrictions/Guard require firmware testing.")
    if values.intersection({"sony"}):
        return Profile("sony", note="Use standard HOME first; no ad package list is validated.")
    if values.intersection({"coocaa", "skyworth"}):
        return Profile("coocaa", note="Manufacturer-specific HOME and ads await hardware verification.")
    if values.intersection({"hisense", "vidaa"}):
        return Profile("hisense", note="Check OS family. VIDAA Linux is not Android.")
    return Profile("android", note="No manufacturer-specific ad/HOME operations validated.")
