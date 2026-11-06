# Paused video screensaver repair — 2026-11-05

Initial local repair based on v2.0.22, followed by the v2.0.23 unified-timer validation.

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

## v2.0.23 — 2026-11-06

- Before the new build, a natural system timeout entered PhotoDream at 19:10:27 local TV time, confirming the former 10-minute system path was still separate from the 1-minute Home preference.
- Both Standard and Guard: 195 JVM tests each, no failures; both release builds and full Android lint passed. 75 Python installer/tool tests, installer shell contracts and locale checks passed.
- Standard APK verified as an in-place upgrade from official v2.0.22 (versionCode 30, unchanged signer), then installed successfully with data retained.
- With verified native HOME and existing local ADB / usage access plus WRITE_SETTINGS authorized, takeover automatically used 60000 ms from the existing 1-minute preference. Standard sleep_timeout and attentive_timeout became -1.
- Turning unified mode off restored exactly 600000 / 86400000 / 86400000, then re-enabling took ownership again.
- HGTV remained explicitly paused. At 19:23:31 PhotoDream accepted the paused player and power state became Dreaming. Screenshot showed the actual frame. This used the configured 1-minute interval, not a temporary system-timeout override.
- No vendor-private timers or stock packages were modified. Other models, HDMI-CEC, scheduled shutdown and cold boot are not hardware certified.
- HGTV was resumed with MEDIA_PLAY. Its media session remained PLAYING for more than the configured 1-minute idle interval; no dream was active and HGTV retained focus. MEDIA_PAUSE restored the prior paused state afterwards. Prolonged playback and casting are still not certified.
