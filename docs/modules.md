# Module architecture / 模块架构

Development branch only; no production rollout or hardware certification.
仅开发分支实现；未部署，未新增实机认证。

## Audit / 审计

- Installation already has `install.sh` for connection, release checksum, in-place
  upgrade, state snapshots and restore; `check-install.py` for observation;
  `tv-replacement.py` for maintenance; `xiaomi_home.py` for reversible HOME trials.
  Reuse them, rather than copy ADB or vendor commands into each strategy.
  安装、校验、覆盖升级、快照、恢复及小米试验已有实现，继续复用。
- `tv/TvControls.kt` delegates the `TvAdapter` interface in `AndroidAdapter.kt` to
  Sony/Xiaomi/TCL/Coocaa adapters. This boundary already works and is preserved.
  设备能力层已有统一接口，本次不改各品牌实现。
- `Weather.kt`, Dock/HomeActivity, ControlCenter and renderers are shared, but
  extension metadata was implicit. `features/FeatureSpec.kt` records their contract;
  the existing weather preference now consumes its default (false).
  通用功能不按品牌复制；补充功能契约，天气默认仍关闭，旧配置键不变。
- Missing seams: deterministic strategy selection and shared website strategy data;
  maintenance previously accepted unknown firmware with consent alone. Added the
  registry, plan entry point and an exact-firmware ad gate. No Gradle module split,
  plugin loader, reflection or background service was needed.
  新增统一策略及网站共享数据，补齐广告精确固件门槛；不重写工程结构。

## Dependency rules / 依赖规则

Host: `install-plan.py` → `installation_strategy.py` + `installation_strategies.json`
→ existing inspection and `install.sh` executor. `tv-replacement.py plan` exposes
the same selection. Website `StrategyGuide.astro` imports the same JSON.
Runtime: shared features → `TvControls` / `TvAdapter` → vendor APIs or Android fallback.
Only metadata and concepts cross the host/runtime boundary; no Python in the APK.
安装、网站共享策略数据；功能通过设备接口使用系统能力。宿主与 APK 不互相调用。

## Add an installation strategy / 增加安装策略

1. Copy a row in `tools/installation_strategies.json`; assign a unique ID, Android
   platform, normalized brands, optional **exact** fingerprints, minimum SDK and
   priority. Add bilingual name/guidance, prerequisites, detection, executor, APK,
   HOME plan, verification, failure, recovery, risk, evidence and validation status.
   复制现有条目，填写唯一 ID、平台、品牌、精确固件、优先级及完整中英文契约。
2. Prefer a firmware rule over a new mechanism. Higher priority wins; a top-priority
   tie blocks all execution. Incomplete identity, unknown platform or unsupported
   SDK blocks writes. Model and MIUI/HyperOS strings never authorize system changes.
   优先扩展固件规则；同优先级冲突停止执行，信息不足不猜测。
3. Use existing `legacy` or `apk-only` executors. A genuinely new mechanism needs a
   reviewed host implementation, rollback and mock tests; never remote arbitrary code.
   复用现有执行器；新机制必须有实现、恢复和测试，不执行远程未审核代码。
4. Add a fixture and assert `select()` plus command capture in
   `tools/test_installation_strategy.py`. The existing generic row is a runnable
   minimum example: change fixture brand to `ExampleTV`, run the test, then run
   `python tools/install-plan.py --platform android --serial <IP>:5555` for inspection.
   通用 Android 条目就是最小可运行示例；先模拟测试，再只读检查实机。

AI, automatic and manual users all start from that command (PowerShell `python`,
macOS/Linux `python3`). Append `--execute` to install. Bash/curl/adb/Python are required;
Windows needs Git Bash on PATH. The default safe route installs with `-r`, snapshots
first, then stops without HOME/Guard changes. Sony/TCL use the existing executor.
三种方式使用同一入口；安全路径只覆盖安装并保存状态，Sony/TCL 保留旧执行器。

TCL verification rejection is not a signing conflict: keep the existing Guard +
`tools/tcast-push.py` manual recovery path in AGENTS.md; do not automatically uninstall
or assume every TCL needs Guard. A failure remains a failure until TV acceptance.
TCL 验证器拒绝与签名冲突不同；保留专用推送及 Guard，不自动卸载或泛化。

## Add a device adapter / 增加设备能力适配器

Extend `AndroidAdapter`, override only measured differences and register once in
`TvControls.brands`. `CoocaaAdapter.kt` is the small existing example. Maintain URI
formats for saved HDMI tiles. Return unavailable/null brightness if there is no safe
API; UI must not pretend success. Keep blocking calls off the UI thread. No APK
installation, default HOME modification, or ad maintenance belongs in these adapters.
继承 AndroidAdapter，只覆盖有证据的差异，在 TvControls 注册一次；亮度不可用返回空，
保持 HDMI URI 与旧设置兼容；安装、HOME 修改和广告清理不属于这里。

Validate both flavors, then test actual volume/brightness/HDMI/settings and recovery
on the device. Mock success is not hardware evidence.
构建两种安装包后实测核心功能；模拟通过不算实机通过。

## Add a shared feature / 增加通用功能

`FeatureSpec` defines ID/name/purpose, minimum SDK, capability requirements, stable
preference keys, default state, permissions, lifecycle, fallback and test method.
Use `Features.weather` and `FeatureSpecTest` as the runnable minimum integration:
Prefs consumes its default; Weather retains the shared provider/cache; HomeActivity
owns visibility, request scheduling and destroyed-activity callback checks.
契约描述 ID、用途、系统要求、能力、配置、默认状态、权限、生命周期、降级和测试。
天气是已接入示例：Prefs 使用契约默认值，数据和缓存保留一份，界面由 HomeActivity 管理。

New features initialize only when enabled/supported; the owning Activity/View starts
resources and cancels listeners/jobs on stop/destroy. No new background service or
dynamic loader. Check capability results at use time, not brand names. HDMI/brightness
call TvControls; null means unavailable. Optional visual backends retain current auto,
glass/panel and focus settings; no renderer algorithm or existing layout is changed.
新功能按需初始化，由宿主管理释放；检查真实能力而非品牌。视觉后端继续尊重用户设置，
不改现有效果；天气没有默认显示或定位请求。

## Firmware evidence, verification and recovery / 固件证据、验收与恢复

Record manufacturer, brand, model, SDK, incremental build, exact fingerprint,
original HOME, candidates, resolver and Guard observation. Distinguish observed
facts, inferred strategy, unknown restrictions and hardware verification.
记录品牌、型号、SDK、版本、指纹及 HOME/守护状态；区分观测事实、策略推断、未知和实测。

Evidence reviewed 2026-10-26: [tv-fresh](https://github.com/wangrongding/tv-fresh)
reports MiTV-ASTP0 / Android 9 and preference/FallbackHome competition; it does not
provide Tigris firmware certification. Do not copy its stock HOME/settings removal.
[Issue 8](https://github.com/lesnolie/Tigris/issues/8) reports service restrictions;
[Issue 14](https://github.com/lesnolie/Tigris/issues/14) reports FFalcon Guard behavior.
These identify investigations, not permission or proof that another firmware works.
上述外部资料只提供排查线索，不构成新固件认证或系统组件禁用授权。

Ad maintenance is separate: `VERIFIED_AD_FIRMWARES` is intentionally empty. A reviewed
entry must prove each package's purpose, exact firmware scope, enabled state and
recoverability; protected core packages remain denied. Per-package consent and the
exact live fingerprint are still required. Snapshot before mutation, verify after,
restore only originally enabled packages. Unknown purpose means no operation.
广告审查表刻意留空；必须有用途、精确固件和恢复证据，再逐包授权；未知组件不操作。

Use `install.sh restore` with the original computer's original snapshot for existing
installs. Xiaomi opt-in trials use `home-probe.json` and `home-restore`; never guess a
launcher or overwrite another user's accessibility services. Failed trials restore
in `finally`; failed ad operations keep a recoverable state file. No sleep/reboot
without user permission. Preserve official signing and in-place updates.
恢复使用原始快照，小米试验使用自己的恢复记录；不猜桌面、不覆盖其他服务，不擅自重启。

Report each result independently: APK, synthetic ADB HOME, physical remote HOME,
cold boot, standby/wake, memory cleanup, ads, HDMI/core functions and restore.
Allowed values: passed / failed / unverified / not applicable. All new hardware
results in this change are unverified. Never claim speed improvements without before/
after measurements; package removal is not a performance benchmark.
逐项记录通过、失败、未验证或不适用；本次无新增实机结果，无性能提升承诺。

## Tests and contribution / 测试与贡献

```
python -m unittest discover -s tools -p "test_*.py" -v
bash tools/test-install.sh
./gradlew :app:testStandardDebugUnitTest :app:testGuardDebugUnitTest :app:assembleStandardDebug :app:assembleGuardDebug
cd website
pnpm install --frozen-lockfile
pnpm check
pnpm build
python ../tools/check-website-install.py
```

Windows uses `gradlew.bat`; run shell contracts in Git Bash. Submit a focused PR with
module ID, fixture, test commands/results, firmware evidence and rollback limits.
Do not update unrelated brand installers to add a UI feature, or change UI modules to
support a TV. CI checks Python/shell contracts, Android flavors and both website locales.
Windows 使用 gradlew.bat；提交独立模块及对应测试、证据和恢复边界，不修改无关层。
