# Third-party notices

- **Inter** typeface (`app/src/main/assets/inter.ttf`) — Copyright (c) 2016 The Inter Project Authors, SIL Open Font License 1.1. Full text: `app/src/main/assets/inter-OFL.txt`.
- **Material Icons** (vector paths in `app/src/main/res/drawable/ic_*.xml`) — Copyright Google LLC, Apache License 2.0, https://github.com/google/material-design-icons.
- **dadb** (build dependency) — Copyright mobile.dev, Apache License 2.0.
- **AndroidX Media3 ExoPlayer** (build dependency) — Copyright The Android Open Source Project, Apache License 2.0.
- **NanoHTTPD** (build dependency) — Copyright (c) 2012-2013 by Paul S. Hawke, 2001,2005-2013 by Jarno Elonen, 2010 by Konstantinos Togias, BSD 3-Clause License.
- **ZXing core** (build dependency) — Copyright ZXing authors, Apache License 2.0.
- **Aerial** (https://github.com/pengchujin/aerial-tv) — MIT License, Copyright (c) 2026 pengchujin. Tigris ports its home guard and revival (`Guard.kt`, `HomeGuardService.kt`, `RevivalSync.kt`, `StockHomeCatalog.kt`), including the later Home-key broadcast, Xiaomi pause bounce, boot and wake checks, vendor HOME categories, usage-stats fallback, deaf-binding recovery, sync-adapter revival and Direct Boot handling. Also Xiaomi device details (`tv/XiaomiAdapter.kt`, `AppRepo.kt`), the phone page (`PhoneServer.kt`, `assets/web/index.html`, restyled), `tools/tcast-push.py`, and the aerial film catalogue `app/src/main/assets/aerials.json` (addresses and place names from Apple's public tvOS aerial resources; the films themselves stream from Apple and are not included).
- **open-tv** (https://github.com/luckfunc/open-tv) — MIT License, Copyright (c) 2026 luckfunc (OpenTV modifications) and Copyright (c) 2026 pengchujin. Tigris ports the optional persistent Home-key filter (`HomeKeyFilterPolicy.kt`, the “持续过滤主页键” setting): Android 12 and newer, off by default, not enabled on Xiaomi, with a warning that some apps may see a press twice.
- **Apple Root CA** (`app/src/main/res/raw/apple_root_ca.pem`) — Apple's public root certificate, trusted only for `sylvan.apple.com`, which serves the aerial films.


## 2.0.4 bundled presentation assets

The project owner supplied `app/src/main/res/raw/default_intro.mp4` (Apple TV
startup animation, 1080p) and `app/src/main/res/drawable-nodpi/default_settings_bg.png`
(Apple-branded settings artwork) for bundling. Apple names and logos remain
their owners' trademarks; these files are not covered by Tigris's source-code
license. Tigris is independent and not endorsed by Apple. User-uploaded videos
remain the responsibility of their uploader and retain their original rights.

The website's muted startup preview uses a compressed excerpt of this
owner-supplied clip for project documentation. It is not covered by Tigris's
source-code license and should not be republished without permission.

The website comparison animates an owner-supplied screenshot of a Chinese-market
TV launcher. It is not an interaction recording or a same-device before/after
test. Depicted brands and media retain their respective rights.

The aerial preview records the owner's television playing the Apple-hosted
screensaver. The app streams aerials and does not bundle the films.
