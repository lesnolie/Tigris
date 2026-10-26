# Recent issue and PR review — 2026-10-26

Review of the two most recent open issues and related PRs. No issue comments or
PR reviews were sent. After the user authorized release, PR #49 and PR #50 were
merged; contributor reports are evidence, not certification of other TVs.

## #45 / PR #46 — Sony registration loop

- Issue: https://github.com/lesnolie/Tigris/issues/45
- PR: https://github.com/lesnolie/Tigris/pull/46
- Reviewed head: `f0343d271b260ce504d6ecf8de80d7090492f1a0`.
- The current main branch always calls `authCookie()` before every BRAVIA request.
  Failure leaves no cookie and no backoff, so subsequent calls repeat `actRegister`.
  The code supports the reported repeated registration-dialog cause.
- The PR avoids authentication for the three reported public getters, keeps auth
  for setters and `getPlayingContentInfo`, and backs off unsuccessful registration
  for 30 minutes. This is a useful mitigation and matches the contributor's device
  observations. Those observations were not independently replayed here.
- **Remaining gap:** the backoff is process-local and `ControlCenter.refresh()`
  still calls the auth-requiring `currentInput()` during Home resume. Restarting
  Tigris can automatically trigger another registration dialog; after 30 minutes
  the attempt is allowed again. The PR also documents this limitation. It fixes the
  infinite loop, not fully unsolicited pairing prompts.
- Recommendation: retain the public-read split; add an explicit user pairing/retry
  path and a persistent failure/suppression state with a deliberate reset. Add a
  regression test that repeated Home refreshes do not repeatedly register, public
  reads never register, and explicit pairing can recover from failure. No check
  runs were reported for the PR at review time. Do not call it fully verified.

## #47 — HY300 Pro+ local debugging authorization

- Issue: https://github.com/lesnolie/Tigris/issues/47
- Reported: GOOJODOQ HY300 Pro+, Android 14, Standard 2.0.9 installs/launches,
  but local debugging authorization immediately fails without a prompt; USB and
  wireless debugging enabled.
- `LocalAdb.shell()` in `Cleaner.kt` only connects to `127.0.0.1:5555` through Dadb.
  It implements neither wireless-debugging pairing nor dynamic-port discovery.
  The catch logs the underlying exception but presents a generic failure to users.
- **Inference:** wireless-debugging availability does not establish that the local
  legacy 5555 endpoint exists. A refused endpoint or unsupported pairing protocol
  would fail before any RSA authorization dialog. The issue alone cannot distinguish
  that from a firmware restriction or authorization timeout.
- Next evidence needed from that projector: the `LocalAdb` exception and its
  cause, actual listening ADB endpoint/port, whether legacy TCP ADB is enabled,
  and whether desktop ADB reaches it. Keep the launcher usable when this optional
  privileged channel is unavailable; distinguish connection refusal from pending
  authorization and explain the limitation. Do not automatically toggle system
  debugging settings or assume success from the Android version/brand.
- No corresponding PR or projector log was present at review time; not fixed or
  verified on that model in this task.

## PR #49 — updater progress/recovery

- https://github.com/lesnolie/Tigris/pull/49
- Merged on 2026-10-26 after Android CI and the signed updater test build passed.
- The official-signed `2.0.10-updater-test` package was installed on the user's Sony TV.

## PR #50 — live wallpaper glass and update-page layout

- https://github.com/lesnolie/Tigris/pull/50
- Merged on 2026-10-26 after Android CI passed.
- A signed test build was checked on the Sony BRAVIA. The live video showed
  through the Dock glass as scenes changed, without the previously visible lag.
  The right-hand update list stayed below the title and scrolled inside its area.
- The device reports a 3840×2160 panel at 60 Hz and a 1920×1080 Android logical
  display size; this was not a 3840×2160 logical-rendering test.
