# 安装、升级与还原

## Direct APK boundary / 直接 APK 的能力边界

APK installed ≠ HOME takeover ≠ system ads removed. A sideload may leave Tigris as
an ordinary app; remote Home, reboot or wake may return to the factory launcher.
Some firmware rejects Standard and needs TCL/FFalcon Guard + T-Cast, or Xiaomi/Redmi
HOME diagnostics. APKs grant neither ADB nor system privileges, do not clean ads,
and cannot bypass background/accessibility restrictions. Android version, CPU ABI,
signature and upgrade compatibility must match. LG webOS and Samsung Tizen cannot
run these APKs. Keep official signing, update in place, save original state, and do
not uninstall to bypass signing conflicts: settings, folders and layouts may be lost.

APK 安装成功不等于 HOME 接管或广告清理成功。安装后可能只是普通应用；实体 Home、
重启、待机唤醒可能返回原厂桌面。部分 TCL/雷鸟固件需要 Guard 与特殊推送，小米/Redmi
需要额外诊断。APK 不自动获得 ADB/系统权限、清理广告或解除后台/无障碍限制。
Android 版本、CPU 架构、签名与升级兼容性必须匹配；不适用于 webOS/Tizen。
保留官方签名、覆盖升级与原始状态；不要为绕过签名而卸载，避免设置、布局和文件夹丢失。

For the **unreleased development checkout** shared AI/manual/automatic strategy
entry point and contributor contracts, see [Module guide](modules.md).
开发分支的统一安装入口尚未发布，见[模块指南](modules.md)；线上入口仍按已发布能力使用。

> **统一安装验收 / Shared compatibility rules:** [按品牌排查、遥控器/U 盘安装、电脑检查与四项验收](installation-compatibility.md). AI 与手动安装使用同一个 `tools/check-install.py`；先验证 Home，失败才处理品牌限制。APK 安装不等于桌面接管，未测项目必须标记未验证。


## 1. 准备

官方安装包需要 Android 9 或更新版本。电脑与电视连接同一网络，开启电视的网络 ADB 调试（Android 调试桥，用于电脑连接电视并安装应用），记下电视 IP。第一次连接时在电视上选择「允许」并勾选「始终允许」。各品牌操作见 [AGENTS.md](../AGENTS.md)。

## 2. 安装

**让 Agent 装（推荐）**：把这段发给能在电脑上执行命令、且已授权访问本机的 Agent，例如 Codex、Claude Code，或具备命令执行能力的 WorkBuddy、豆包电脑版/工作版、千问电脑版、Trae：

```text
请帮我把开源电视桌面 Tigris 装到电视上，并设置默认桌面或桌面守护。
电视 IP：<电视IP>
保持 ADB 常开：要

安装说明：https://github.com/lesnolie/Tigris/blob/main/AGENTS.md
打不开时：https://raw.githubusercontent.com/lesnolie/Tigris/main/AGENTS.md

请先完整阅读安装说明，再在我的电脑上亲自运行命令，不要只告诉我步骤。
需要我在电视上操作时提醒我。不要擅自重启、卸载旧版、清空配置或停用原厂桌面。
签名不匹配时先寻找旧版签名密钥，不要自动卸载。
最后按「验收」原样贴出以下输出，每条 adb 都加 -s <电视IP>:5555：
- adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
- adb shell settings get secure enabled_accessibility_services
- 按主页键并等待两秒后，adb shell "dumpsys window | grep mCurrentFocus"
安装成功、主页键生效、待机唤醒和开机测试请分别说明；没有测过的不要说通过。
```

关键是 Agent **能在你的电脑上执行命令**。仅聊天的手机 App 或网页不会替你安装，具体取决于版本和授权能力。看验收的 `mCurrentFocus` 是否包含 **`com.leslie.tvhome`**，不能只看 Agent 说“装好了”。守护模式下默认 HOME 可能仍是原厂桌面，以按主页后的前台为准。

**一行命令（macOS / Linux）**：先安装 Android Platform-Tools（`adb`）、`curl` 和 Python 3，把 IP 换成自己的电视：

```bash
curl -fsSL https://raw.githubusercontent.com/lesnolie/Tigris/main/install.sh | KEEP_ADB=1 TV=192.168.1.20 bash
```

默认下载最新**正式版**并校验 SHA-256，不自动安装预发布。指定版本可加 `VERSION=v2.0.4`；Guard 版末尾用 `bash -s -- -s guard`。TCL 拒绝 ADB 安装时，脚本停止，按 [多屏互动说明](../AGENTS.md#3-install) 推送 Guard 包。


**手动**：从 [最新正式版 Release](https://github.com/lesnolie/Tigris/releases/latest) 选择带版本号的标准版 APK（如 `Tigris-v2.0.6.apk`），或者运行以下脚本自动下载并校验。先记录原配置，遇到失败先处理再继续：

```bash
adb connect 192.168.1.20:5555
# 保存原桌面输出，便于还原。
adb -s 192.168.1.20:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
curl -fsSL -o download-release.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/download-release.py
APK=$(python3 download-release.py standard)
adb -s 192.168.1.20:5555 install -r "$APK"
adb -s 192.168.1.20:5555 shell pm grant com.leslie.tvhome android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.1.20:5555 shell cmd notification allow_listener com.leslie.tvhome/com.leslie.tvhome.RevivalListener
adb -s 192.168.1.20:5555 shell appops set com.leslie.tvhome SYSTEM_ALERT_WINDOW allow
adb -s 192.168.1.20:5555 shell cmd package set-home-activity com.leslie.tvhome/.HomeActivity
adb -s 192.168.1.20:5555 shell am start -n com.leslie.tvhome/.HomeActivity
```

Windows 用 Agent 或 PowerShell：`curl.exe` 下载脚本，再运行 `$apk = python .\download-release.py standard`，随后 `adb -s 192.168.1.20:5555 install -r $apk`，无需 Bash。小米/TCL 等仍被原桌面接管时按 [守护说明](../AGENTS.md#10-tvs-whose-system-fights-third-party-launchers-the-home-guard) 配置。

**没有电脑**：项目没有逐一验证手机上的 ADB 工具。只用 U 盘装 APK 不等于设好了默认桌面和开机进入，建议用电脑完成验收。

**升级**：再次执行安装命令，`adb install -r` 在签名一致时保留配置。2.0.4 起可从「控制中心 → 设置 → 软件更新与投屏」检查并安装更新；旧版需先再运行一次安装命令。2.0.4 起发布流程使用固定签名；旧预发布包可能使用不同密钥，`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 表示要用旧密钥重新签名，不要自动卸载。

## 还原

使用原来安装时的电脑、电视地址和 `STATE_DIR`（若自定义过）：

```bash
curl -fsSL https://raw.githubusercontent.com/lesnolie/Tigris/main/install.sh | TV=192.168.1.20 bash -s -- restore
```

脚本关闭 Tigris 守护和保持 ADB 开启，恢复记录的原桌面；仅当前屏保为 Tigris 时恢复原屏保。默认保留 App 和配置；显式添加 `--uninstall`（`bash -s -- restore --uninstall`）才卸载。

原桌面记录默认在电脑的 `~/.local/state/tigris/<电视IP>:5555/`，设过 `XDG_STATE_HOME` 时跟随该目录。升级不覆盖记录。记录缺失或已是 Tigris 时脚本停止，按 [人工还原](../AGENTS.md#restore) 查明原桌面，不猜包名。

交给 Agent 还原可复制：

```text
请完整阅读 https://github.com/lesnolie/Tigris/blob/main/AGENTS.md 的 Restore 部分，
把电视（IP：<电视IP>）恢复到原桌面，Tigris 先不卸载。
请在我的电脑上亲自执行，保留其他无障碍服务，不要猜原桌面包名。
最后原样贴出 enabled_accessibility_services 和按主页键后的 mCurrentFocus 输出。
```

## 常见问题

- **只装 APK，主页键还是原桌面**：还需要默认 HOME 或桌面守护配置。
- **TCL / 雷鸟拒绝安装**：用 Guard 包和多屏互动，电视弹出提示时选择「同意」。
- **`KEEP_ADB=1`**：明确启用“保持 ADB 调试开启”，用于固件待机后关闭 ADB 的情况。未指定时保留已有选择，新安装默认关闭，还原时关闭。
- **系统太旧**：官方包最低 Android 9，`INSTALL_FAILED_OLDER_SDK` 无法靠更换安装器解决。LG webOS、三星 Tizen 等非 Android 系统不能安装。
- **原桌面、账号、会员还在吗**：安装器不卸载或停用原桌面，不清除其他应用数据。固件开机广告不属于桌面，不能承诺去除。
- **安装成功却不能开机进入**：主页、待机唤醒和开机要分别验证，重启需用户同意；机型范围见 README。

## 它怎么接管桌面

原生 Android TV 通常直接设为默认桌面。部分国产固件需要无障碍守护检测前台窗口，将原厂桌面切回 Tigris；通知监听帮助被系统清理后的恢复。守护不读取屏幕内容，通知监听不读取通知内容。安装器不会关闭原厂桌面，也不会自动重启或关闭电视。
