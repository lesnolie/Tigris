# Modular installation validation / 模块化验收

Date / 日期: 2026-10-26. Branch: `feat/xiaomi-home-diagnostics`.
Scope: development code only; no main changes, PR merge, release or production deployment.
范围：仅开发分支代码；不合并、不发版、不部署。

## Local checks / 本地验证

| Command | Result / 结果 |
|---|---|
| `python -m unittest discover -s tools -p 'test_*.py' -q` | 65 passed / 通过 |
| `bash tools/test-install.sh` (Git Bash) | Installer contracts passed / 安装契约通过 |
| `gradlew.bat --no-daemon :app:testStandardDebugUnitTest :app:testGuardDebugUnitTest :app:assembleStandardDebug :app:assembleGuardDebug` | 82 tests; both Debug builds passed / 两种版本通过 |
| `gradlew.bat --no-daemon :app:assembleStandardRelease :app:assembleGuardRelease :app:lintStandardRelease :app:lintGuardRelease` | Both Release builds and lint passed; local build only / 本地构建及静态检查通过 |
| `python tools/test_i18n.py` | 379 English + 379 Chinese resources / 中英文资源通过 |
| `node tools/test_phone_i18n.cjs` | Passed / 通过 |
| `pnpm install --frozen-lockfile`, `pnpm check`, `pnpm build` in website | Passed; 4 existing type-check hints, no errors / 通过，4 个既有提示 |
| `python tools/check-website-install.py` | Built Chinese/English download warnings, links, commands, preview labels and recovery passed / 构建产物检查通过 |
| `git diff --check` | Passed / 通过 |

CI is run on PR #43 after pushing; the final task response reports the actual run
status and head commit, not the previous branch's green checks.
推送后检查 PR #43 当前提交的 CI；最终回复给出实际状态，不沿用旧提交的通过记录。

Ego browser checked 390px English mobile layout and 1024px Chinese desktop layout:
no document horizontal overflow, both APK warnings adjacent to official release
links. Mobile screenshot visually inspected. Some large screenshot requests timed
out; DOM layout checks completed. New strategy UI explicitly says development preview.
浏览器检查手机及电脑宽度无整页横向溢出，下载旁显示限制；手机截图已人工式查看。
部分大截图请求超时，DOM 布局检查完成；新功能明确标注未发布。

## Regression boundaries / 回归边界

Sony/TCL existing installer execution remains intact; tests cover native HOME,
explicit TCL Guard autostart, HOME failure, invalid baseline, safe upgrade, signature
conflict, checksums and recovery. T-Cast implementation, runtime adapters, Guard,
HomeActivity, Weather provider and renderers are unchanged. The only existing
runtime behavior source edit uses the feature contract's false weather default;
the preference key and saved user value are unchanged.
保留 Sony/TCL 执行器及特殊推送、运行时适配器、守护、视觉与布局实现。天气仅从契约读取
同样的 false 默认值，旧设置键及用户值不变。

Python fixtures cover strategy conflicts, exact firmware rules, MIUI/HyperOS labels,
non-Android rejection before ADB, missing authorization, unknown identity, Xiaomi
missing HOME candidates, PatchWall/FallbackHome routing, ineffective preference,
failed trial recovery and unknown-firmware ad rejection. The Windows shell fixture
was repaired to route **all** Python ADB subprocesses through mock ADB.
模拟测试覆盖策略冲突、未知固件、平台/授权、HOME 竞争及恢复；Windows 测试隔离已修复。

## Hardware report / 实机报告

No TV was supplied or modified in this task. Every row below is unverified for
Sony, TCL/FFalcon and Xiaomi/Redmi in this change. Earlier reports remain historical
evidence, not newly verified firmware claims.
本任务未提供或操作电视；所有品牌以下项目均未验证，历史反馈不算本次认证。

| Check / 项目 | Result / 结果 |
|---|---|
| APK installation / 安装 | Unverified / 未验证 |
| Synthetic ADB Home / 模拟 Home | Unverified / 未验证 |
| Physical remote Home / 实体 Home | Unverified / 未验证 |
| Cold boot / 冷开机 | Unverified / 未验证 |
| Standby/wake / 待机唤醒 | Unverified / 未验证 |
| Memory cleanup recovery / 清理内存后恢复 | Unverified / 未验证 |
| Advertising / 广告变化 | Unverified; no approved firmware / 未验证，无批准固件 |
| HDMI, volume, settings, casting, OTA / 核心功能 | Unverified / 未验证 |
| Original launcher recovery / 恢复原桌面 | Unverified / 未验证 |
| Performance / 性能 | Not measured; no improvement claim / 未测量，不宣称改善 |

Next hardware work is bounded: capture exact firmware and independent results for
these checks. Do not expand to disabling core components. Windows real-device
installation also awaits hardware; its shell syntax and mock execution are checked.
后续只需按精确固件逐项实测，不扩展到禁用核心组件；Windows 实机安装同样等待设备。
