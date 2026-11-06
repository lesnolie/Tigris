# Paused video screensaver repair — 2026-11-05

Local repair based on v2.0.22; not an upstream release.

Device: Sony BRAVIA 4K VH2, Android 12 / SDK 31, firmware 682241.

Observed failure: PhotoDream rejected the system idle request with `foreign foreground has no verified paused media state`, then Android slept due to timeout. Usage Access was default/denied. After granting it, the DreamActivity resume event replaced the underlying player's foreground identity. After filtering that overlay, the media controller API still returned an empty list during dream attachment.

Changes:
- Granted GET_USAGE_STATS for the installed app.
- Excluded Android's DreamActivity overlay from content foreground tracking.
- Added an off-main-thread fallback to read `dumpsys media_session` using the already authorized local ADB connection when the regular API exposes no foreign sessions. Retained explicit paused states remain useful when a player deactivates its session on losing focus.
- Kept foreground matching, active playback and casting suppression. Missing authorization or unreadable state does not infer paused playback.

Validation:
- 191 JVM tests passed, including the overlay/paused-player sequence and dump parsing with active-playback suppression.
- Standard release APK built and installed in place; same signing certificate as official v2.0.22. Data retained.
- HGTV (`com.hgtv.star`) explicitly paused, system idle triggered PhotoDream, log accepted the paused foreground video, power state was Dreaming, screenshot showed the photo frame.
- Temporary timeout changes restored to the original 600000 ms (10 minutes).
- Long-duration natural timeout, active-playback hardware verification, restart and standby/wake are not covered by this check.
